package vc.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.EmbedData;
import discord4j.discordjson.json.MessageCreateRequest;
import discord4j.rest.entity.RestChannel;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Color;
import org.redisson.api.RBoundedBlockingQueue;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import vc.config.GuildConfigManager;
import vc.config.GuildConfigRecord;
import vc.live.dto.Chats;
import vc.live.dto.Deaths;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.slf4j.LoggerFactory.getLogger;

@Component
public class LiveChat {
    private static final Logger LOGGER = getLogger("LiveChat");
    private final RedisClient redisClient;
    private final GatewayDiscordClient discordClient;
    private final Map<String, RestChannel> liveChatGuilds;
    private final GuildConfigManager guildConfigManager;
    private final RBoundedBlockingQueue<String> chatQueue;
    private final RBoundedBlockingQueue<String> deathQueue;
    private final ConcurrentLinkedQueue<EmbedData> messageQueue;
    private final ScheduledExecutorService executorService;
    private final ObjectMapper objectMapper;
    private final Cache<String, AtomicInteger> guildMessageSendFailCountCache = CacheBuilder.newBuilder()
        .expireAfterWrite(5, MINUTES)
        .build();

    public LiveChat(final RedisClient redisClient,
                    final GatewayDiscordClient discordClient,
                    final GuildConfigManager guildConfigManager,
                    final ScheduledExecutorService executorService,
                    final ObjectMapper objectMapper
    ) {
        this.redisClient = redisClient;
        this.discordClient = discordClient;
        this.guildConfigManager = guildConfigManager;
        this.executorService = executorService;
        this.objectMapper = objectMapper;
        this.liveChatGuilds = new ConcurrentHashMap<>();
        this.messageQueue = new ConcurrentLinkedQueue<>();
        syncLiveChatGuildIds();
        this.chatQueue = redisClient.getQueue("ChatsQueue");
        this.deathQueue = redisClient.getQueue("DeathsQueue");
        this.executorService.scheduleAtFixedRate(this::processChatQueue, 1, 3, SECONDS);
        this.executorService.scheduleAtFixedRate(this::processDeathQueue, 1, 3, SECONDS);
        this.executorService.scheduleAtFixedRate(this::processMessageQueue, 5, 10, SECONDS);
    }

    private void processMessageQueue() {
        try {
            final List<EmbedData> embeds = new ArrayList<>(4);
            EmbedData embedDate;
            while (embeds.size() < 10 && (embedDate = messageQueue.poll()) != null) {
                embeds.add(embedDate);
            }
            if (embeds.isEmpty()) return;
            // todo: test if we need to use a rate limiter between sending messages to different guilds
            Flux.just(this.liveChatGuilds.entrySet().toArray(new Map.Entry[0]))
                .parallel()
                .runOn(Schedulers.parallel())
                .flatMap(entry -> processSend(entry, embeds))
                .sequential()
                .blockLast();
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
                        disableLiveChat(guildId);
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
                && guildMessageSendFailCountCache.size() < liveChatGuilds.size()
            ) {
                LOGGER.error("Disabling live chat for guild {} due to message send failures", guildId);
                // todo: try sending one last notification message that we disabled live chat?
                disableLiveChat(guildId);
            }
        } catch (final Throwable e) {
            LOGGER.error("Error counting message send failure", e);
        }
    }

    private void syncLiveChatGuildIds() {
        liveChatGuilds.clear();
        guildConfigManager.getAllGuildConfigs().stream()
            .filter(GuildConfigRecord::liveChatEnabled)
            .forEach(config -> liveChatGuilds.put(config.guildId(), discordClient
                .getChannelById(Snowflake.of(config.liveChatChannelId()))
                .map(Channel::getRestChannel)
                .block()));
    }

    public void enableLiveChat(final String guildId, final String channelId) {
        this.guildConfigManager.getGuildConfig(guildId)
            .ifPresentOrElse(guildConfigRecord -> {
                final GuildConfigRecord newRecord = new GuildConfigRecord(guildConfigRecord.guildId(), guildConfigRecord.guildName(), true, channelId);
                this.guildConfigManager.updateGuildConfig(newRecord);
                addLiveChatGuildId(guildId, channelId);
                LOGGER.info("Enabled live chat for guild {}, {}", guildId, guildConfigRecord.guildName());
            }, () -> {
                throw new RuntimeException("Guild config not found");
            });
    }

    public void disableLiveChat(final String guildId) {
        this.guildConfigManager.getGuildConfig(guildId)
            .ifPresentOrElse(guildConfigRecord -> {
                final GuildConfigRecord newRecord = new GuildConfigRecord(guildConfigRecord.guildId(), guildConfigRecord.guildName(), false, guildConfigRecord.liveChatChannelId());
                this.guildConfigManager.updateGuildConfig(newRecord);
                removeLiveChatGuildId(guildId);
                LOGGER.info("Disabled live chat for guild {}, {}", guildId, guildConfigRecord.guildName());
            }, () -> {
                throw new RuntimeException("Guild config not found");
            });
    }

    private RestChannel getRestChannel(final String channelId) {
        return discordClient.getChannelById(Snowflake.of(channelId))
            .map(Channel::getRestChannel)
            .block();
    }

    private void addLiveChatGuildId(final String guildId, final String channelId) {
        liveChatGuilds.put(guildId, getRestChannel(channelId));
    }

    private void removeLiveChatGuildId(final String guildId) {
        liveChatGuilds.remove(guildId);
    }

    private void processChatQueue() {
        try {
            final String chatJson = chatQueue.poll();
            if (chatJson == null) return;
            final Chats chat = objectMapper.readValue(chatJson, Chats.class);
            this.messageQueue.add(getChatEmbed(chat));
        } catch (final Exception e) {
            LOGGER.error("Error processing chat queue", e);
        }
    }

    private void processDeathQueue() {
        try {
            final String deathJson = deathQueue.poll();
            if (deathJson == null) return;
            final Deaths death = objectMapper.readValue(deathJson, Deaths.class);
            this.messageQueue.add(getDeathEmbed(death));
        } catch (final Exception e) {
            LOGGER.error("Error processing chat queue", e);
        }
    }

    private EmbedData getChatEmbed(final Chats chat) {
        return EmbedCreateSpec.builder()
            .description(escape("**" + chat.getPlayerName() + ":** " + chat.getChat()))
            .footer("\u200b", avatarUrl(chat.getPlayerUuid()).toString())
            .color(Color.BLACK)
            .timestamp(Instant.ofEpochSecond(chat.getTime().toEpochSecond()))
            .build()
            .asRequest();
    }

    private EmbedData getDeathEmbed(final Deaths death) {
        return EmbedCreateSpec.builder()
            .description(escape(death.getDeathMessage().replace(death.getVictimPlayerName(), "**" + death.getVictimPlayerName() + "**")))
            .footer("\u200b", avatarUrl(death.getVictimPlayerUuid()).toString())
            .color(Color.RUBY)
            .timestamp(Instant.ofEpochSecond(death.getTime().toEpochSecond()))
            .build()
            .asRequest();
    }

    private URL avatarUrl(final UUID uuid) {
        try {
            return URI.create(String.format("https://minotar.net/helm/%s/64", uuid.toString().replace("-", ""))).toURL();
        } catch (final MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String escape(String message) {
        return message.replaceAll("_", "\\\\_");
    }

    public void onAllGuildsLoaded() {
        syncLiveChatGuildIds();
        LOGGER.info("Loaded {} live chat guilds", liveChatGuilds.size());
    }
}
