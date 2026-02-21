package vc.live;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import vc.config.live_feed.LiveFeedRepository;
import vc.config.live_feed.model.LiveFeedConfig;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.slf4j.LoggerFactory.getLogger;

public abstract class LiveFeed {
    private final Logger LOGGER = getLogger(getClass().getSimpleName());
    private static final int MESSAGE_Q_CAPACITY = 1000;
    private static final int FEED_QUEUE_WARN_THRESHOLD = 100;
    protected final JDA jda;
    protected final Map<String, GuildMessageChannel> liveChannels;
    protected final LiveFeedRepository liveFeedRepository;
    protected final LiveFeedDispatcher dispatcher;
    protected final AtomicBoolean inDispatch = new AtomicBoolean(false);
    private final PriorityBlockingQueue<Message> messageQueue;
    private final Cache<String, AtomicInteger> guildMessageSendFailCountCache = CacheBuilder.newBuilder()
        .expireAfterWrite(5, MINUTES)
        .build();
    protected final FeedApiManager feedListener;

    public LiveFeed(
        final FeedApiManager feedListener,
        final JDA jda,
        final LiveFeedRepository liveFeedRepository,
        final LiveFeedDispatcher dispatcher,
        final boolean liveFeedEnabled
    ) {
        this.feedListener = feedListener;
        this.jda = jda;
        this.liveFeedRepository = liveFeedRepository;
        this.dispatcher = dispatcher;
        this.liveChannels = new ConcurrentHashMap<>();
        this.messageQueue = new PriorityBlockingQueue<>(MESSAGE_Q_CAPACITY);
        if (liveFeedEnabled) {
            LOGGER.info("Starting {} live feed", getClass().getSimpleName());
            syncChannels();
            inputQueues().forEach(this::registerInputQueue);
        } else {
            LOGGER.info("Live feed {} disabled", getClass().getSimpleName());
        }
    }

    protected abstract List<LiveFeedConfig> getAllEnabled();

    protected abstract String liveChannelId(final LiveFeedConfig guildConfigRecord);

    protected abstract LiveFeedConfig disableRecordInternal(final LiveFeedConfig in);

    protected abstract LiveFeedConfig enableRecordInternal(final LiveFeedConfig in, final String guildId, final String channelId);

    protected abstract List<InputQueue> inputQueues();

    protected abstract LiveFeedDispatcher.FeedLane feedLane();

    record InputQueue<T>(
        String name,
        Consumer<Consumer> feedConsumer,
        Class<T> deserializedType,
        Function<T, MessageEmbed> embedBuilderFunction,
        Function<T, Long> timestampFunction
    ) {
    }

    record Message(MessageEmbed embedData, long timestamp) implements Comparable<Message> {
        @Override
        public int compareTo(final Message o) {
            return Long.compare(timestamp(), o.timestamp());
        }
    }

    private void registerInputQueue(final InputQueue inputQueue) {
        ((Consumer<Consumer>) inputQueue.feedConsumer()).accept(v -> inputQueueListener(inputQueue, v));
        LOGGER.info("Registered {} listener", inputQueue.name());
    }

    private void inputQueueListener(final InputQueue inputQueue, final Object value) {
        synchronized (this.messageQueue) {
            this.messageQueue.add(new Message((MessageEmbed) inputQueue.embedBuilderFunction().apply(value), (long) inputQueue.timestampFunction().apply(value)));
        }
    }

    private String feedName() {
        return getClass().getSimpleName();
    }

    public void syncChannels() {
        liveChannels.clear();
        var allConfigs = getAllEnabled();
        for (var config : allConfigs) {
            var channel = getGuildMessageChannel(liveChannelId(config));
            if (channel == null) {
                LOGGER.info("Disabling {} for guild: {} due to missing channel", feedName(), config.guildId());
                disableFeed(config.guildId());
                continue;
            }
            liveChannels.put(config.guildId(), channel);
        }
    }

    public void disableFeed(final String guildId) {
        liveFeedRepository.getByGuild(guildId)
            .ifPresent(guildConfigRecord -> {
                final LiveFeedConfig newRecord = disableRecordInternal(guildConfigRecord);
                liveFeedRepository.write(newRecord);
                LOGGER.info("Disabled {} for guild {}, {}", feedName(), guildId, guildConfigRecord.guildName());
            });
        this.liveChannels.remove(guildId);
    }

    public void enableFeed(final String guildId, final String channelId) {
        Optional<LiveFeedConfig> guildConfigOptional = liveFeedRepository.getByGuild(guildId);
        if (guildConfigOptional.isEmpty()) {
            var guild = jda.getGuildById(guildId);
            if (guild != null) {
                var config = LiveFeedConfig.defaultConfig(guildId, guild.getName());
                liveFeedRepository.write(config);
                guildConfigOptional = liveFeedRepository.getByGuild(guildId);
            }
            if (guildConfigOptional.isEmpty()) {
                LOGGER.error("Error getting guild data to create record for guild: {}", guildId);
                guildConfigOptional = Optional.of(LiveFeedConfig.defaultConfig(guildId, ""));
            }
        }
        var guildConfigRecord = guildConfigOptional.get();
        final LiveFeedConfig newRecord = enableRecordInternal(guildConfigRecord, guildId, channelId);
        liveFeedRepository.write(newRecord);
        var channel = getGuildMessageChannel(channelId);
        if (channel != null) this.liveChannels.put(guildId, channel);
        LOGGER.info("Enabled {} for guild {}, {}", feedName(), guildId, guildConfigRecord.guildName());
    }

    private GuildMessageChannel getGuildMessageChannel(final String channelId) {
        try {
            var channel = jda.getGuildChannelById(channelId);
            if (channel instanceof GuildMessageChannel messageChannel) {
                return messageChannel;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Scheduled(initialDelay = 5, fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    protected void processMessageQueue() {
        if (!inDispatch.compareAndSet(false, true)) return;
        try {
            final List<MessageEmbed> embeds = new ArrayList<>(4);
            int staleDropped = 0;
            synchronized (this.messageQueue) {
                Message message;
                var now = Instant.now().toEpochMilli();
                while (embeds.size() < 10 && (message = messageQueue.poll()) != null) {
                    if (now - message.timestamp > MINUTES.toMillis(20)) {
                        staleDropped++;
                        continue;
                    }
                    embeds.add(message.embedData());
                }
            }
            if (staleDropped > 0) {
                LOGGER.info("[{}] Dropped {} stale feed events", feedName(), staleDropped);
            }
            if (embeds.isEmpty()) {
                inDispatch.set(false);
                return;
            }

            var channels = new ArrayList<>(liveChannels.entrySet());
            Collections.shuffle(channels);
            long beforeAll = System.currentTimeMillis();
            dispatcher.submitBatch(feedLane(), channels, embeds, this::handleBroadcastError)
                .whenComplete((ok, error) -> {
                    inDispatch.set(false);
                    if (error != null) {
                        LOGGER.error("[{}] Error sending feed batch", feedName(), error);
                    }
                    long afterAll = System.currentTimeMillis();
                    if (afterAll - beforeAll > 20000) {
                        LOGGER.info("[{}] Sent {} events in {}ms", feedName(), embeds.size(), afterAll - beforeAll);
                    }
                });
        } catch (final Throwable e) {
            LOGGER.error("Error processing message queue", e);
            inDispatch.set(false);
        }
    }

    @Scheduled(fixedRate = 30, initialDelay = 30, timeUnit = TimeUnit.SECONDS)
    protected void reportQueueHealth() {
        final int localQueueDepth = messageQueue.size();
        final int dispatchDepth = dispatcher.pendingDepth(feedLane());
        if (localQueueDepth == 0 && dispatchDepth == 0) return;

        LOGGER.debug(
            "queueDepth={} dispatchDepth={} liveChannels={}",
            localQueueDepth,
            dispatchDepth,
            liveChannels.size()
        );

        if (localQueueDepth >= FEED_QUEUE_WARN_THRESHOLD) {
            LOGGER.warn(
                "Input queue depth high: {} (capacity {})",
                localQueueDepth,
                MESSAGE_Q_CAPACITY
            );
        }
    }

    private void handleBroadcastError(final String guildId, final Throwable error) {
        var channel = liveChannels.get(guildId);
        if (error instanceof ErrorResponseException e) {
            var response = e.getErrorResponse();
            if (response == ErrorResponse.MISSING_PERMISSIONS || response == ErrorResponse.MISSING_ACCESS || response == ErrorResponse.UNKNOWN_CHANNEL) {
                LOGGER.error("Missing permissions while broadcasting message to channel: {}", channel == null ? "unknown" : channel.getId());
                disableFeed(guildId);
                return;
            }
        } else if (error instanceof InsufficientPermissionException e) {
            LOGGER.error("Missing permissions while broadcasting message to channel: {}", channel == null ? "unknown" : channel.getId());
            disableFeed(guildId);
            return;
        }
        LOGGER.error("Error broadcasting message to guild: {}", guildId, error);
        // todo: not sure the full set of exception jda throws
//        countMessageSendFailure(guildId);
    }

    private void countMessageSendFailure(final String guildId) {
        try {
            int failCount = guildMessageSendFailCountCache
                .get(guildId, () -> new AtomicInteger(0))
                .incrementAndGet();
            if (failCount > 5 && guildMessageSendFailCountCache.size() < liveChannels.size()) {
                LOGGER.error("Disabling {} for guild {} due to message send failures", feedName(), guildId);
                disableFeed(guildId);
            }
        } catch (final Throwable e) {
            LOGGER.error("Error counting message send failure", e);
        }
    }

    protected URL avatarUrl(final UUID uuid) {
        try {
            return URI.create(String.format("https://mc-heads.net/avatar/%s/64", uuid.toString().replace("-", ""))).toURL();
        } catch (final MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void onAllGuildsLoaded() {
        syncChannels();
        LOGGER.info("Loaded {} live guilds", liveChannels.size());
    }
}
