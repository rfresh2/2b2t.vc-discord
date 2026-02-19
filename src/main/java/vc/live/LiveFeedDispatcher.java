package vc.live;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import static org.slf4j.LoggerFactory.getLogger;

@Component
public class LiveFeedDispatcher {
    private static final Logger LOGGER = getLogger(LiveFeedDispatcher.class);
    private static final int BACKLOG_WARN_THRESHOLD = 5_000;

    public enum FeedLane {
        CHAT,
        CONNECTIONS
    }

    private final EnumMap<FeedLane, ArrayDeque<DispatchTask>> lanes = new EnumMap<>(FeedLane.class);
    private final Object lock = new Object();
    private final double requestsPerSecond;
    private final double maxTokens;
    private FeedLane nextLane = FeedLane.CHAT;
    private double tokens;
    private long lastRefillNanos;
    private final AtomicLong submittedSinceLastReport = new AtomicLong(0);
    private final AtomicLong dispatchedSinceLastReport = new AtomicLong(0);
    private final AtomicLong failedSinceLastReport = new AtomicLong(0);

    public LiveFeedDispatcher(
        @Value("${LIVE_FEED_REQUESTS_PER_SECOND:40}")
        final double requestsPerSecond,
        @Value("${LIVE_FEED_BURST_CAPACITY:40}")
        final double maxTokens
    ) {
        this.requestsPerSecond = Math.max(1.0, requestsPerSecond);
        this.maxTokens = Math.max(1.0, maxTokens);
        this.tokens = this.maxTokens;
        this.lastRefillNanos = System.nanoTime();
        lanes.put(FeedLane.CHAT, new ArrayDeque<>());
        lanes.put(FeedLane.CONNECTIONS, new ArrayDeque<>());
    }

    public CompletableFuture<Void> submitBatch(
        final FeedLane lane,
        final List<Map.Entry<String, GuildMessageChannel>> channels,
        final List<MessageEmbed> embeds,
        final BiConsumer<String, Throwable> errorHandler
    ) {
        if (channels.isEmpty() || embeds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        final List<MessageEmbed> immutableEmbeds = List.copyOf(embeds);
        final CompletableFuture<Void> done = new CompletableFuture<>();
        final BatchState state = new BatchState(channels.size(), done);

        synchronized (lock) {
            for (var entry : channels) {
                lanes.get(lane).addLast(new DispatchTask(entry.getKey(), entry.getValue(), immutableEmbeds, errorHandler, state));
            }
            submittedSinceLastReport.addAndGet(channels.size());
        }
        return done;
    }

    @Scheduled(fixedRate = 100, initialDelay = 100)
    void dispatch() {
        while (tryDispatchOne()) {
            // keep dispatching while there are tokens and pending tasks
        }
    }

    private boolean tryDispatchOne() {
        final DispatchTask task;
        synchronized (lock) {
            refillTokensLocked();
            if (tokens < 1.0) return false;

            task = pollNextTaskLocked();
            if (task == null) return false;

            tokens -= 1.0;
        }

        try {
            task.channel().sendMessageEmbeds(task.embeds())
                .submit(true)
                .whenComplete((message, error) -> {
                    try {
                        dispatchedSinceLastReport.incrementAndGet();
                        if (error != null) {
                            failedSinceLastReport.incrementAndGet();
                            task.errorHandler().accept(task.guildId(), error);
                        }
                    } finally {
                        task.batchState().onTaskDone();
                    }
                });
        } catch (Throwable t) {
            try {
                failedSinceLastReport.incrementAndGet();
                task.errorHandler().accept(task.guildId(), t);
            } finally {
                dispatchedSinceLastReport.incrementAndGet();
                task.batchState().onTaskDone();
            }
        }
        return true;
    }

    public int pendingDepth(final FeedLane lane) {
        synchronized (lock) {
            return lanes.get(lane).size();
        }
    }

    @Scheduled(fixedRate = 30, initialDelay = 30, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    void reportStats() {
        final long submitted = submittedSinceLastReport.getAndSet(0);
        final long dispatched = dispatchedSinceLastReport.getAndSet(0);
        final long failed = failedSinceLastReport.getAndSet(0);

        final int chatDepth;
        final int connectionsDepth;
        final int totalDepth;
        final int availableTokens;
        synchronized (lock) {
            refillTokensLocked();
            chatDepth = lanes.get(FeedLane.CHAT).size();
            connectionsDepth = lanes.get(FeedLane.CONNECTIONS).size();
            totalDepth = chatDepth + connectionsDepth;
            availableTokens = (int) tokens;
        }

        if (submitted == 0 && dispatched == 0 && totalDepth == 0 && failed == 0) return;

        final long etaSeconds = requestsPerSecond <= 0 ? -1 : (long) Math.ceil(totalDepth / requestsPerSecond);
        LOGGER.info(
            "submitted={} dispatched={} failed={} backlogTotal={} backlogChat={} backlogConnections={} tokens={} approxDrain={}s",
            submitted,
            dispatched,
            failed,
            totalDepth,
            chatDepth,
            connectionsDepth,
            availableTokens,
            Math.max(0, etaSeconds)
        );

        if (totalDepth >= BACKLOG_WARN_THRESHOLD) {
            LOGGER.warn(
                "Backlog high: total={} chat={} connections={} approxDrain={}s",
                totalDepth,
                chatDepth,
                connectionsDepth,
                Math.max(0, etaSeconds)
            );
        }
    }

    private void refillTokensLocked() {
        final long now = System.nanoTime();
        final long delta = now - lastRefillNanos;
        if (delta <= 0) return;

        final double toAdd = (delta / 1_000_000_000.0) * requestsPerSecond;
        tokens = Math.min(maxTokens, tokens + toAdd);
        lastRefillNanos = now;
    }

    private DispatchTask pollNextTaskLocked() {
        final var firstLane = nextLane;
        final var secondLane = firstLane == FeedLane.CHAT ? FeedLane.CONNECTIONS : FeedLane.CHAT;

        DispatchTask task = lanes.get(firstLane).pollFirst();
        if (task != null) {
            nextLane = secondLane;
            return task;
        }

        task = lanes.get(secondLane).pollFirst();
        if (task != null) {
            nextLane = firstLane;
            return task;
        }
        return null;
    }

    private record DispatchTask(
        String guildId,
        GuildMessageChannel channel,
        List<MessageEmbed> embeds,
        BiConsumer<String, Throwable> errorHandler,
        BatchState batchState
    ) {
    }

    private static class BatchState {
        private final AtomicInteger remaining;
        private final CompletableFuture<Void> done;

        private BatchState(final int size, final CompletableFuture<Void> done) {
            this.remaining = new AtomicInteger(size);
            this.done = done;
        }

        private void onTaskDone() {
            if (remaining.decrementAndGet() == 0) {
                done.complete(null);
            }
        }
    }
}
