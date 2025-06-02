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
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import vc.config.GuildConfigManager;
import vc.config.GuildConfigRecord;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.slf4j.LoggerFactory.getLogger;

public abstract class LiveFeed {
    private final Logger LOGGER = getLogger(getClass().getSimpleName());
    private static final int MESSAGE_Q_CAPACITY = 1000;
    protected final RedisClient redisClient;
    protected final GatewayDiscordClient discordClient;
    protected final Map<String, RestChannel> liveChannels;
    protected final Map<InputQueue, RReliableTopic> inputTopics;
    protected final GuildConfigManager guildConfigManager;
    private final PriorityBlockingQueue<Message> messageQueue;
    private final ScheduledExecutorService executorService;
    private final ObjectMapper objectMapper;
    private final Cache<String, AtomicInteger> guildMessageSendFailCountCache = CacheBuilder.newBuilder()
        .expireAfterWrite(5, MINUTES)
        .build();
    private ScheduledFuture<?> processMessageQueueFuture;

    public LiveFeed(final RedisClient redisClient,
                    final GatewayDiscordClient discordClient,
                    final GuildConfigManager guildConfigManager,
                    final ScheduledExecutorService executorService,
                    final ObjectMapper objectMapper,
                    final boolean liveFeedEnabled) {
        this.redisClient = redisClient;
        this.discordClient = discordClient;
        this.liveChannels = new ConcurrentHashMap<>();
        this.guildConfigManager = guildConfigManager;
        this.messageQueue = new PriorityBlockingQueue<>(MESSAGE_Q_CAPACITY);
        this.inputTopics = new ConcurrentHashMap<>();
        this.executorService = executorService;
        this.objectMapper = objectMapper;
        if (liveFeedEnabled) {
            LOGGER.info("Starting {} live feed", getClass().getSimpleName());
            syncChannels();
            this.processMessageQueueFuture = this.executorService.scheduleWithFixedDelay(this::processMessageQueue, ((int) (Math.random() * 10)), 10, SECONDS);
            inputQueues().forEach(this::registerInputQueue);
        } else {
            LOGGER.info("Live feed {} disabled", getClass().getSimpleName());
        }
    }

    protected abstract boolean channelEnabledPredicate(final GuildConfigRecord guildConfigRecord);
    protected abstract String liveChannelId(final GuildConfigRecord guildConfigRecord);

    protected abstract GuildConfigRecord disableRecordInternal(final GuildConfigRecord in);

    protected abstract GuildConfigRecord enableRecordInternal(final GuildConfigRecord in, final String guildId, final String channelId);

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
        inputTopics.put(inputQueue, topic);
        topic.addListener(String.class, (channel, message) -> topicMessageListener(inputQueue, message));
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

    private String feedName() {
        return getClass().getSimpleName();
    }

    public void syncChannels() {
        liveChannels.clear();
        var allConfigs = guildConfigManager.getAllGuildConfigs();
        for (var config : allConfigs) {
            if (!channelEnabledPredicate(config)) continue;
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
        this.guildConfigManager.getGuildConfig(guildId)
            .ifPresentOrElse(guildConfigRecord -> {
                final GuildConfigRecord newRecord = disableRecordInternal(guildConfigRecord);
                this.guildConfigManager.updateGuildConfig(newRecord);
                LOGGER.info("Disabled {} for guild {}, {}", feedName(), guildId, guildConfigRecord.guildName());
            }, () -> LOGGER.info("Guild: {} config not found while disabling {} feed", guildId, feedName()));
        this.liveChannels.remove(guildId);
    }

    public void enableFeed(final String guildId, final String channelId) {
        Optional<GuildConfigRecord> guildConfigOptional = this.guildConfigManager.getGuildConfig(guildId);
        if (guildConfigOptional.isEmpty()) {
            try {
                this.guildConfigManager.loadGuild(guildId).block();
                guildConfigOptional = this.guildConfigManager.getGuildConfig(guildId);
            } catch (final Exception e) {
                LOGGER.error("Error loading guild data to create record for guild: {}", guildId, e);
            }
            if (guildConfigOptional.isEmpty()) {
                LOGGER.error("Error getting guild data to create record for guild: {}", guildId);
                guildConfigOptional = Optional.of(new GuildConfigRecord(guildId, "", false, "", false, ""));
            }
        }
        var guildConfigRecord = guildConfigOptional.get();
        final GuildConfigRecord newRecord = enableRecordInternal(guildConfigRecord, guildId, channelId);
        this.guildConfigManager.updateGuildConfig(newRecord);
        this.liveChannels.put(guildId, getRestChannel(channelId));
        LOGGER.info("Enabled {} for guild {}, {}", feedName(), guildId, guildConfigRecord.guildName());
    }

    private RestChannel getRestChannel(final String channelId) {
        return discordClient.getChannelById(Snowflake.of(channelId))
            .map(Channel::getRestChannel)
            .block();
    }

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
                // missing permissions or channel deleted, disable immediately
                LOGGER.error("Missing permissions while broadcasting message to channel: {}", channel.getId().asString());
                disableFeed(guildId);
                return;
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

    protected String escape(String message) {
        return message.replaceAll("_", "\\\\_");
    }

    public void onAllGuildsLoaded() {
        syncChannels();
        LOGGER.info("Loaded {} live guilds", liveChannels.size());
    }
}
