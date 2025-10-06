package vc.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.Channel;
import discord4j.discordjson.json.EmbedData;
import discord4j.discordjson.json.MessageCreateRequest;
import discord4j.rest.entity.RestChannel;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.MultipartRequest;
import org.redisson.api.RReliableTopic;
import org.slf4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import vc.config.live_feed.LiveFeedRepository;
import vc.config.live_feed.model.LiveFeedConfig;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.slf4j.LoggerFactory.getLogger;

public abstract class LiveFeed implements DisposableBean {
    private final Logger LOGGER = getLogger(getClass().getSimpleName());
    private static final int MESSAGE_Q_CAPACITY = 1000;
    protected final RedisClient redisClient;
    protected final GatewayDiscordClient discordClient;
    protected final Map<String, RestChannel> liveChannels;
    protected final Map<InputQueue, TopicListener> inputTopics;
    protected final LiveFeedRepository liveFeedRepository;
    private final PriorityBlockingQueue<Message> messageQueue;
    private final ObjectMapper objectMapper;
    private final Cache<String, AtomicInteger> guildMessageSendFailCountCache = CacheBuilder.newBuilder()
        .expireAfterWrite(5, MINUTES)
        .build();

    public LiveFeed(
        final RedisClient redisClient,
        final GatewayDiscordClient discordClient,
        final LiveFeedRepository liveFeedRepository,
        final ObjectMapper objectMapper,
        final boolean liveFeedEnabled
    ) {
        this.redisClient = redisClient;
        this.discordClient = discordClient;
        this.liveFeedRepository = liveFeedRepository;
        this.liveChannels = new ConcurrentHashMap<>();
        this.messageQueue = new PriorityBlockingQueue<>(MESSAGE_Q_CAPACITY);
        this.inputTopics = new ConcurrentHashMap<>();
        this.objectMapper = objectMapper;
        if (liveFeedEnabled) {
            LOGGER.info("Starting {} live feed", getClass().getSimpleName());
            syncChannels();
            inputQueues().forEach(this::registerInputQueue);
        } else {
            LOGGER.info("Live feed {} disabled", getClass().getSimpleName());
        }
    }

    record TopicListener(
        RReliableTopic topic,
        String id
    ) {}

    protected abstract List<LiveFeedConfig> getAllEnabled();
    protected abstract boolean channelEnabledPredicate(final LiveFeedConfig guildConfigRecord);
    protected abstract String liveChannelId(final LiveFeedConfig guildConfigRecord);

    protected abstract LiveFeedConfig disableRecordInternal(final LiveFeedConfig in);

    protected abstract LiveFeedConfig enableRecordInternal(final LiveFeedConfig in, final String guildId, final String channelId);

    protected abstract List<InputQueue> inputQueues();

    record InputQueue<T>(
        String topicName,
        Class<T> deserializedType,
        Function<T, EmbedData> embedBuilderFunction,
        Function<T, Long> timestampFunction
    ) {}

    record Message(EmbedData embedData, long timestamp) implements Comparable<Message> {
        @Override
        public int compareTo(final Message o) {
            return Long.compare(timestamp(), o.timestamp());
        }
    }

    private void registerInputQueue(final InputQueue inputQueue) {
        final RReliableTopic topic = this.redisClient.getTopic(inputQueue.topicName());
        String id = topic.addListener(String.class, (channel, message) -> topicMessageListener(inputQueue, message));
        inputTopics.put(inputQueue, new TopicListener(topic, id));
        LOGGER.info("Registered {} topic listener {}", inputQueue.topicName(), id);
    }

    private void topicMessageListener(final InputQueue inputQueue, final String message) {
        try {
            var data = objectMapper.readValue(message, inputQueue.deserializedType());
            synchronized (this.messageQueue) {
                this.messageQueue.add(new Message((EmbedData) inputQueue.embedBuilderFunction().apply(data), (long) inputQueue.timestampFunction().apply(data)));
            }
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to deserialize message: {}", message, e);
        }
    }

    @Scheduled(initialDelay = 1, fixedRate = 1, timeUnit = HOURS)
    private void refreshTopicListeners() {
        for (var entry : inputTopics.entrySet()) {
            try {
                var topicListener = entry.getValue();
                topicListener.topic().removeListener(topicListener.id());
                String id = topicListener.topic().addListener(String.class, (channel, message) -> topicMessageListener(entry.getKey(), message));
                inputTopics.remove(entry.getKey());
                inputTopics.put(entry.getKey(), new TopicListener(topicListener.topic(), id));
                LOGGER.info("Refreshed {} topic listener {}", entry.getKey().topicName(), id);
            } catch (Exception e) {
                LOGGER.error("Error refreshing topic listener for {}", entry.getKey().topicName(), e);
            }
        }
    }

    private String feedName() {
        return getClass().getSimpleName();
    }

    public void syncChannels() {
        liveChannels.clear();
        var allConfigs = getAllEnabled();
        for (var config : allConfigs) {
            try {
                var channel = discordClient
                    .getChannelById(Snowflake.of(liveChannelId(config)))
                    .map(Channel::getRestChannel)
                    .block();
                liveChannels.put(config.guildId(), channel);
            } catch (final Exception e) {
                LOGGER.error("Error getting channel: {} for guild: {} - {}", liveChannelId(config), config.guildId(), e.getMessage());
                if (e instanceof ClientException clientException && clientException.getStatus().code() == 404) {
                    LOGGER.info("Disabling {} for guild: {} due to missing channel", feedName(), config.guildId());
                    disableFeed(config.guildId());
                }
            }
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
            try {
                var guild = discordClient.getGuildById(Snowflake.of(guildId)).block();
                var guildData = guild.getData();
                var config = LiveFeedConfig.defaultConfig(guildId, guildData.name());
                liveFeedRepository.write(config);
                guildConfigOptional = liveFeedRepository.getByGuild(guildId);
            } catch (final Exception e) {
                LOGGER.error("Error loading guild data to create record for guild: {}", guildId, e);
            }
            if (guildConfigOptional.isEmpty()) {
                LOGGER.error("Error getting guild data to create record for guild: {}", guildId);
                guildConfigOptional = Optional.of(LiveFeedConfig.defaultConfig(guildId, ""));
            }
        }
        var guildConfigRecord = guildConfigOptional.get();
        final LiveFeedConfig newRecord = enableRecordInternal(guildConfigRecord, guildId, channelId);
        liveFeedRepository.write(newRecord);
        this.liveChannels.put(guildId, getRestChannel(channelId));
        LOGGER.info("Enabled {} for guild {}, {}", feedName(), guildId, guildConfigRecord.guildName());
    }

    private RestChannel getRestChannel(final String channelId) {
        return discordClient.getChannelById(Snowflake.of(channelId))
            .map(Channel::getRestChannel)
            .block();
    }

    @Scheduled(initialDelay = 5, fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    protected void processMessageQueue() {
        try {
            final List<EmbedData> embeds = new ArrayList<>(4);
            synchronized (this.messageQueue) {
                Message message;
                var now = Instant.now().getEpochSecond();
                while (embeds.size() < 10 && (message = messageQueue.poll()) != null) {
                    if (now - message.timestamp > MINUTES.toSeconds(20)) continue;
                    embeds.add(message.embedData());
                }
            }
            if (embeds.isEmpty()) return;
            final MultipartRequest<MessageCreateRequest> request = MultipartRequest.ofRequest(MessageCreateRequest.builder()
                .embeds(embeds)
                .build());
            var channels = new ArrayList<>(liveChannels.entrySet());
            Collections.shuffle(channels);
            Lists.partition(channels, 50).forEach(c -> {
                long before = System.currentTimeMillis();
                Flux.fromIterable(c)
                    .flatMap(entry -> processSend(entry.getKey(), entry.getValue(), request))
                    .doOnError(error -> LOGGER.error("Error processing message queue", error))
                    .blockLast();
                long after = System.currentTimeMillis();
                if (after - before > 20000) {
                    LOGGER.info("[{}] Sent {} events in {}ms", feedName(), embeds.size(), after - before);
                }
            });
        } catch (final Throwable e) {
            LOGGER.error("Error processing message queue", e);
        }
    }

    private Mono<?> processSend(String guildId, RestChannel channel, MultipartRequest<MessageCreateRequest> request) {
        return channel.createMessage(request)
            .doOnError(error -> LOGGER.error("Error sending message to guild: {}, channelId: {}", guildId, channel.getId().asString(), error))
            .timeout(Duration.ofSeconds(3))
            // retry only on TimeoutException
            .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(1))
                .filter(error -> error instanceof TimeoutException)
                .onRetryExhaustedThrow((spec, signal) -> Exceptions.retryExhausted(
                    "Retries exhausted sending message to guild: " + guildId + ", channelId: " + channel.getId().asString(),
                    signal.failure())))
            .onErrorResume(error -> {
                if (Exceptions.isRetryExhausted(error))
                    handleBroadcastError(error.getCause(), guildId, channel);
                else
                    handleBroadcastError(error, guildId, channel);
                return Mono.empty();
            });
    }

    private void handleBroadcastError(final Throwable error, final String guildId, final RestChannel channel) {
        if (error instanceof ClientException e) {
            int code = e.getStatus().code();
            if (code == 429) {
                // rate limit
                LOGGER.error("Rate limited while broadcasting message to channel: {}", channel.getId().asString());
                return;
            } else if (code == 403 || code == 404) {
                var cloudflareError = e.getErrorResponse()
                    .map(r -> r.getFields().get("body"))
                    .filter(body -> body instanceof String)
                    .map(body -> (String) body)
                    .map(body -> body.contains("cloudflare"))
                    .orElse(false);
                if (cloudflareError) {
                    LOGGER.error("Cloudflare error while broadcasting message to channel: {}", channel.getId().asString(), error);
                    return;
                } else {
                    // missing permissions or channel deleted, disable immediately
                    LOGGER.error("Missing permissions while broadcasting message to channel: {}", channel.getId().asString());
                    disableFeed(guildId);
                    return;
                }
            }
        }
        // for any unknown error, count it and disable if we get too many
        LOGGER.error("Error broadcasting message to guild: {}", guildId, error);
        countMessageSendFailure(guildId);
    }

    private void countMessageSendFailure(final String guildId) {
        try {
            int failCount = guildMessageSendFailCountCache
                .get(guildId, () -> new AtomicInteger(0))
                .incrementAndGet();
            if (failCount > 5
                // sanity check that we aren't disabling when msgs to all guilds are failing
                && guildMessageSendFailCountCache.size() < liveChannels.size()
            ) {
                LOGGER.error("Disabling {} for guild {} due to message send failures", feedName(), guildId);
                // todo: try sending one last notification message that we disabled live feed?
                disableFeed(guildId);
            }
        } catch (final Throwable e) {
            LOGGER.error("Error counting message send failure", e);
        }
    }

    protected URL avatarUrl(final UUID uuid) {
        try {
            return URI.create(String.format("https://minotar.net/helm/%s/64", uuid.toString().replace("-", ""))).toURL();
        } catch (final MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void onAllGuildsLoaded() {
        syncChannels();
        LOGGER.info("Loaded {} live guilds", liveChannels.size());
    }

    @Override
    public void destroy() {
        LOGGER.info("Shutting down {} live feed", getClass().getSimpleName());
        inputTopics.values().forEach(topicListener -> {
            try {
                topicListener.topic.removeListener(topicListener.id);
            } catch (final Exception e) {
                LOGGER.error("Error removing topic listener for {}", topicListener.topic.getName(), e);
            }
        });
    }
}
