package vc.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.Channel;
import discord4j.discordjson.json.EmbedData;
import discord4j.discordjson.json.MessageCreateRequest;
import discord4j.rest.entity.RestChannel;
import discord4j.rest.http.client.ClientException;
import org.redisson.api.RBoundedBlockingQueue;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import vc.config.GuildConfigManager;
import vc.config.GuildConfigRecord;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.slf4j.LoggerFactory.getLogger;

public abstract class LiveFeed {
    private final Logger LOGGER = getLogger(getClass().getSimpleName());
    protected final RedisClient redisClient;
    protected final GatewayDiscordClient discordClient;
    protected final Map<String, RestChannel> liveChannels;
    protected final Map<InputQueue, RBoundedBlockingQueue> inputQueues;
    protected final GuildConfigManager guildConfigManager;
    private final ConcurrentLinkedQueue<EmbedData> messageQueue;
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
                    final ObjectMapper objectMapper) {
        this.redisClient = redisClient;
        this.discordClient = discordClient;
        this.liveChannels = new ConcurrentHashMap<>();
        this.guildConfigManager = guildConfigManager;
        this.messageQueue = new ConcurrentLinkedQueue<>();
        this.inputQueues = new ConcurrentHashMap<>();
        this.executorService = executorService;
        this.objectMapper = objectMapper;
        syncChannels();
        this.processMessageQueueFuture = this.executorService.scheduleWithFixedDelay(this::processMessageQueue, ((int) (Math.random() * 10)), 10, SECONDS);
        inputQueues().forEach(this::monitorQueue);
    }

    record InputQueue<T>(String queueName, Class<T> deserializedType, Function<T, EmbedData> embedBuilderFunction) {}

    private void monitorQueue(final InputQueue inputQueue) {
        final RBoundedBlockingQueue<String> queue = this.redisClient.getQueue(inputQueue.queueName());
        inputQueues.put(inputQueue, queue);
        this.executorService.scheduleWithFixedDelay(() -> processInputQueue(queue, inputQueue), 1, 3, SECONDS);
    }

    private String feedName() {
        return getClass().getSimpleName();
    }

    private void processInputQueue(final RBoundedBlockingQueue<String> queue, final InputQueue inputQueue) {
        try {
            final String json = queue.poll();
            if (json == null) return;
            final Object data = objectMapper.readValue(json, inputQueue.deserializedType);
            this.messageQueue.add((EmbedData) inputQueue.embedBuilderFunction.apply(data));
        } catch (final Exception e) {
            LOGGER.error("Error processing {} queue", feedName(), e);
        }
    }

    protected abstract boolean channelEnabledPredicate(final GuildConfigRecord guildConfigRecord);
    protected abstract String liveChannelId(final GuildConfigRecord guildConfigRecord);

    protected abstract GuildConfigRecord disableRecordInternal(final GuildConfigRecord in);

    protected abstract GuildConfigRecord enableRecordInternal(final GuildConfigRecord in, final String guildId, final String channelId);

    protected abstract List<InputQueue> inputQueues();

    public void syncChannels() {
        liveChannels.clear();
        guildConfigManager.getAllGuildConfigs().stream()
            .filter(this::channelEnabledPredicate)
            .forEach(config -> liveChannels.put(config.guildId(), discordClient
                .getChannelById(Snowflake.of(liveChannelId(config)))
                .map(Channel::getRestChannel)
                .block()));
    }

    public void disableFeed(final String guildId) {
        this.guildConfigManager.getGuildConfig(guildId)
            .ifPresentOrElse(guildConfigRecord -> {
                final GuildConfigRecord newRecord = disableRecordInternal(guildConfigRecord);
                this.guildConfigManager.updateGuildConfig(newRecord);
                this.liveChannels.remove(guildId);
                LOGGER.info("Disabled {} for guild {}, {}", feedName(), guildId, guildConfigRecord.guildName());
            }, () -> {
                throw new RuntimeException("Guild config not found");
            });
    }

    public void enableFeed(final String guildId, final String channelId) {
        this.guildConfigManager.getGuildConfig(guildId)
            .ifPresentOrElse(guildConfigRecord -> {
                final GuildConfigRecord newRecord = enableRecordInternal(guildConfigRecord, guildId, channelId);
                this.guildConfigManager.updateGuildConfig(newRecord);
                this.liveChannels.put(guildId, getRestChannel(channelId));
                LOGGER.info("Enabled {} for guild {}, {}", feedName(), guildId, guildConfigRecord.guildName());
            }, () -> {
                throw new RuntimeException("Guild config not found");
            });
    }

    private RestChannel getRestChannel(final String channelId) {
        return discordClient.getChannelById(Snowflake.of(channelId))
            .map(Channel::getRestChannel)
            .block();
    }

    protected void processMessageQueue() {
        try {
            final List<EmbedData> embeds = new ArrayList<>(4);
            EmbedData embedDate;
            while (embeds.size() < 10 && (embedDate = messageQueue.poll()) != null) {
                embeds.add(embedDate);
            }
            if (embeds.isEmpty()) return;
            // todo: test if we need to use a rate limiter between sending messages to different guilds
            Flux.fromIterable(liveChannels.entrySet())
                .parallel()
                .runOn(Schedulers.parallel())
                .flatMap(entry -> processSend(entry, embeds))
                .sequential()
                .blockLast(Duration.ofSeconds(20));
        } catch (final Throwable e) {
            LOGGER.error("Error processing message queue", e);
        }
    }

    private Mono<?> processSend(final Map.Entry<String, RestChannel> entry, final List<EmbedData> embeds) {
        final RestChannel channel = entry.getValue();
        final String guildId = entry.getKey();
        return channel.createMessage(
                MessageCreateRequest.builder()
                    .embeds(embeds)
                    .build())
            .onErrorResume(error -> {
                if (error instanceof ClientException e) {
                    int code = e.getStatus().code();
                    if (code == 429) {
                        // rate limit
                        LOGGER.error("Rate limited while broadcasting message to channel: {}", channel.getId().asString());
                        return Mono.empty();
                    } else if (code == 403 || code == 404) {
                        // missing permissions or channel deleted, disable immediately
                        LOGGER.error("Missing permissions while broadcasting message to channel: {}", channel.getId().asString());
                        disableFeed(guildId);
                        return Mono.empty();
                    }
                }
                // for any unknown error, count it and disable if we get too many
                countMessageSendFailure(guildId);
                LOGGER.error("Error broadcasting message to guild: {}", guildId, error);
                return Mono.empty();
            });
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
