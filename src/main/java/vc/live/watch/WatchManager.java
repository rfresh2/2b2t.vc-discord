package vc.live.watch;

import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.util.MentionUtil;
import discord4j.discordjson.json.UserGuildData;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.Color;
import org.redisson.api.RReliableTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import vc.api.model.ProfileDataImpl;
import vc.config.watch.GuildChatWatchRepository;
import vc.config.watch.GuildPlayerWatchRepository;
import vc.config.watch.UserChatWatchRepository;
import vc.config.watch.UserPlayerWatchRepository;
import vc.config.watch.model.GuildPlayerWatchConfig;
import vc.config.watch.model.GuildWatch;
import vc.config.watch.model.UserPlayerWatchConfig;
import vc.config.watch.model.UserWatch;
import vc.live.RedisClient;
import vc.live.dto.ChatsRecord;
import vc.live.dto.ConnectionsRecord;
import vc.live.dto.DeathsRecord;
import vc.live.dto.enums.Connectiontype;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static vc.util.DiscordMarkdownEscape.escape;

@Component
public class WatchManager implements DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchManager.class);
    private final GuildChatWatchRepository guildChatWatchRepository;
    private final GuildPlayerWatchRepository guildPlayerWatchRepository;
    private final UserChatWatchRepository userChatWatchRepository;
    private final UserPlayerWatchRepository userPlayerWatchRepository;
    private final RedisClient redisClient;
    private final GatewayDiscordClient discordClient;
    private final ObjectMapper objectMapper;
    private final boolean watchesEnabled;
    private final ConcurrentLinkedDeque<ConnectionsRecord> joinsQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<ConnectionsRecord> leavesQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<ChatsRecord> chatsQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<ChatsRecord> chatsKeywordQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<DeathsRecord> deathsQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<DeathsRecord> killsQueue = new ConcurrentLinkedDeque<>();
    RReliableTopic connectionsTopic;
    RReliableTopic chatsTopic;
    RReliableTopic deathsTopic;
    String connectionsTopicId;
    String chatsTopicId;
    String deathsTopicId;

    public WatchManager(
        final GuildChatWatchRepository guildChatWatchRepository,
        final GuildPlayerWatchRepository guildPlayerWatchRepository,
        final UserChatWatchRepository userChatWatchRepository,
        final UserPlayerWatchRepository userPlayerWatchRepository,
        final RedisClient redisClient,
        final GatewayDiscordClient discordClient,
        final ObjectMapper objectMapper,
        @Value("${WATCHES}")
        final String watchesEnabled
    ) {
        this.guildChatWatchRepository = guildChatWatchRepository;
        this.guildPlayerWatchRepository = guildPlayerWatchRepository;
        this.userChatWatchRepository = userChatWatchRepository;
        this.userPlayerWatchRepository = userPlayerWatchRepository;
        this.redisClient = redisClient;
        this.discordClient = discordClient;
        this.objectMapper = objectMapper;
        this.watchesEnabled = Boolean.parseBoolean(watchesEnabled);
        if (this.watchesEnabled) {
            LOGGER.info("Watch manager enabled");
            LOGGER.info("Loaded {} user player watch configs", userPlayerWatchRepository.getAll().size());
            LOGGER.info("Loaded {} guild player watch configs", guildPlayerWatchRepository.getAll().size());
            LOGGER.info("Loaded {} user chat watch configs", userChatWatchRepository.getAll().size());
            LOGGER.info("Loaded {} guild chat watch configs", guildChatWatchRepository.getAll().size());
            connectionsTopic = this.redisClient.getTopic("ConnectionsTopic");
            connectionsTopicId = connectionsTopic.addListener(String.class, (channel, msg) -> connectionsTopicListener(msg));
            chatsTopic = this.redisClient.getTopic("ChatsTopic");
            chatsTopicId = chatsTopic.addListener(String.class, (channel, msg) -> chatsTopicListener(msg));
            deathsTopic = this.redisClient.getTopic("DeathsTopic");
            deathsTopicId = deathsTopic.addListener(String.class, (channel, msg) -> deathsTopicListener(msg));
            LOGGER.info("Watch manager initialized");
        } else {
            LOGGER.info("Watch manager disabled");
        }
    }

    @Scheduled(fixedRate = 1000)
    private void processJoinsQueue() {
        processQueue(
            "Joins",
            joinsQueue,
            this::joinsWatchEmbed,
            ConnectionsRecord::playerUuid,
            ConnectionsRecord::playerName,
            (c, userWatch) -> userWatch.joins(),
            (c, guildWatch) -> guildWatch.joins()
        );
    }

    @Scheduled(fixedRate = 1000)
    private void processLeavesQueue() {
        processQueue(
            "Leaves",
            leavesQueue,
            this::leavesWatchEmbed,
            ConnectionsRecord::playerUuid,
            ConnectionsRecord::playerName,
            (c, userWatch) -> userWatch.leaves(),
            (c, guildWatch) -> guildWatch.leaves()
        );
    }

    @Scheduled(fixedRate = 1000)
    private void processKillsQueue() {
        processQueue(
            "Kills",
            killsQueue,
            this::killsWatchEmbed,
            DeathsRecord::killerPlayerUuid,
            DeathsRecord::killerPlayerName,
            (c, userWatch) -> userWatch.kills(),
            (c, guildWatch) -> guildWatch.kills()
        );
    }

    @Scheduled(fixedRate = 1000)
    private void processDeathsQueue() {
        processQueue(
            "Deaths",
            deathsQueue,
            this::deathsWatchEmbed,
            DeathsRecord::victimPlayerUuid,
            DeathsRecord::victimPlayerName,
            (c, userWatch) -> userWatch.deaths(),
            (c, guildWatch) -> guildWatch.deaths()
        );
    }

    @Scheduled(fixedRate = 1000)
    private void processChatsQueue() {
        processQueue(
            "Chats",
            chatsQueue,
            this::chatsWatchEmbed,
            ChatsRecord::playerUuid,
            ChatsRecord::playerName,
            (c, userWatch) -> userWatch.chats(),
            (c, guildWatch) -> guildWatch.chats()
        );
    }

    @Scheduled(fixedRate = 1000)
    private void processChatsKeywordQueue() {
        try {
            while (!chatsKeywordQueue.isEmpty()) {
                var data = chatsKeywordQueue.poll();
                if (data == null) continue;
                var userWatches = userChatWatchRepository.getAll();
                for (var userWatch : userWatches) {
                    if (userWatch.caseSensitive()
                        ? !data.chat().contains(userWatch.keyword())
                        : !data.chat().toLowerCase().contains(userWatch.keyword().toLowerCase())){
                        continue;
                    }
                    sendUserWatchNotification(
                        "ChatsKeyword",
                        userWatch.keyword(),
                        userWatch,
                        () -> chatsKeywordWatchEmbed(data, userWatch.keyword()),
                        userChatWatchRepository::delete
                    );
                }
                var guildWatches = guildChatWatchRepository.getAll();
                for (var guildWatch : guildWatches) {
                    if (guildWatch.caseSensitive()
                        ? !data.chat().contains(guildWatch.keyword())
                        : !data.chat().toLowerCase().contains(guildWatch.keyword().toLowerCase())){
                        continue;
                    }
                    sendGuildNotification(
                        "ChatsKeyword",
                        guildWatch.keyword(),
                        guildWatch,
                        () -> chatsKeywordWatchEmbed(data, guildWatch.keyword()),
                        guildChatWatchRepository::delete
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error while processing chat keyword watch queue", e);
        }
    }

    private <T> void processQueue(
        String id,
        ConcurrentLinkedDeque<T> queue,
        Function<T, EmbedCreateSpec> watchEmbedProvider,
        Function<T, UUID> targetUuidProvider,
        Function<T, String> targetNameProvider,
        BiFunction<T, UserPlayerWatchConfig, Boolean> userActivePredicate,
        BiFunction<T, GuildPlayerWatchConfig, Boolean> guildActivePredicate
    ) {
        try {
            while (!queue.isEmpty()) {
                var data = queue.poll();
                if (data == null) continue;
                var userWatches = userPlayerWatchRepository.getByTargetUuid(targetUuidProvider.apply(data));
                for (var userWatch : userWatches) {
                    if (!userActivePredicate.apply(data, userWatch)) continue;
                    try {
                        sendUserWatchNotification(
                            id,
                            userWatch.targetName(),
                            userWatch,
                            () -> watchEmbedProvider.apply(data),
                            userPlayerWatchRepository::delete
                        );
                    } catch (Exception e) {
                        LOGGER.error("Error sending user watch target {} notification {}", userWatch.targetName(), userWatch, e);
                    }
                }
                for (var userWatch : userWatches) {
                    if (!targetNameProvider.apply(data).equals(userWatch.targetName())) {
                        LOGGER.info("Updating user watch {} target name from {} to {}", userWatch.watchId(), userWatch.targetName(), targetNameProvider.apply(data));
                        var newConfig = new UserPlayerWatchConfig(
                            userWatch.watchId(),
                            userWatch.ownerUserId(),
                            userWatch.ownerUserName(),
                            userWatch.joins(),
                            userWatch.leaves(),
                            userWatch.chats(),
                            userWatch.deaths(),
                            userWatch.kills(),
                            userWatch.targetUuid(),
                            targetNameProvider.apply(data)
                        );
                        userPlayerWatchRepository.write(newConfig);
                    }
                }
                var guildWatches = guildPlayerWatchRepository.getByTargetUuid(targetUuidProvider.apply(data));
                for (var guildWatch : guildWatches) {
                    if (!guildActivePredicate.apply(data, guildWatch)) continue;
                    sendGuildNotification(
                        id,
                        guildWatch.targetName(),
                        guildWatch,
                        () -> watchEmbedProvider.apply(data),
                        guildPlayerWatchRepository::delete
                    );
                }
                for (var guildWatch : guildWatches) {
                    if (!targetNameProvider.apply(data).equals(guildWatch.targetName())) {
                        LOGGER.info("Updating guild watch {} target name from {} to {}", guildWatch.watchId(), guildWatch.targetName(), targetNameProvider.apply(data));
                        var newConfig = new GuildPlayerWatchConfig(
                            guildWatch.watchId(),
                            guildWatch.guildId(),
                            guildWatch.guildName(),
                            guildWatch.channelId(),
                            guildWatch.joins(),
                            guildWatch.leaves(),
                            guildWatch.chats(),
                            guildWatch.deaths(),
                            guildWatch.kills(),
                            guildWatch.mentionUserId(),
                            guildWatch.mentionRoleId(),
                            guildWatch.targetUuid(),
                            targetNameProvider.apply(data)
                        );
                        guildPlayerWatchRepository.write(newConfig);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error while processing {} queue", id, e);
        }
    }

    private <T extends UserWatch> void sendUserWatchNotification(
        String id,
        String targetName,
        T userWatch,
        Supplier<EmbedCreateSpec> embedSupplier,
        Consumer<T> removeWatchConsumer
    ) {
        try {
            var channel = discordClient.getUserById(Snowflake.of(userWatch.ownerUserId()))
                .doOnSuccess(user -> LOGGER.info("[{}] Sending {} user notification to {}", id, targetName, user.getUsername()))
                .flatMap(User::getPrivateChannel)
                .block(Duration.ofSeconds(10));

            var msg = MessageCreateSpec.builder()
                .addEmbed(embedSupplier.get())
                .build();

            channel.createMessage(msg)
                .doOnError(error -> LOGGER.error("Error sending {} user notification to: {}", targetName, userWatch.ownerUserName()))
                .timeout(Duration.ofSeconds(3))
                .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(1))
                    .filter(error -> error instanceof TimeoutException)
                    .onRetryExhaustedThrow((spec, signal) -> Exceptions.retryExhausted(
                        "Retries exhausted sending " + targetName + " notification to user: " + userWatch.ownerUserName() + ", channelId: " + channel.getId().asString(),
                        signal.failure())))
                .onErrorResume(error -> {
                    if (Exceptions.isRetryExhausted(error)) {
                        if (error instanceof ClientException e) {
                            int code = e.getStatus().code();
                            if (code == 429) {
                                LOGGER.error("Rate limited while sending {} notification to user: {}", targetName, userWatch.ownerUserName());
                            } else if (code == 403 || code == 404) {
                                var cloudflareError = e.getErrorResponse()
                                    .map(r -> r.getFields().get("body"))
                                    .filter(body -> body instanceof String)
                                    .map(body -> (String) body)
                                    .map(body -> body.contains("cloudflare"))
                                    .orElse(false);
                                if (!cloudflareError) {
                                    LOGGER.error("Missing permissions while sending {} notification to user: {}. Removing watch.", targetName, userWatch.ownerUserName());
                                    removeWatchConsumer.accept(userWatch);
                                }
                            }
                        }
                    }
                    LOGGER.error("Error sending {} user notification to: {}", targetName, userWatch.ownerUserName(), error);
                    return Mono.empty();
                })
                .block(Duration.ofSeconds(10));
        } catch (Exception e) {
            LOGGER.error("Error sending {} user notification to {}", targetName, userWatch.ownerUserId(), e);
        }
    }

    private <T extends GuildWatch> void sendGuildNotification(
        String id,
        String targetName,
        T guildWatch,
        Supplier<EmbedCreateSpec> embedSupplier,
        Consumer<T> removeGuildWatchFunction
    ) {
        try {
            var guildSnowflake = Snowflake.of(guildWatch.guildId());
            var guild = discordClient.getGuildById(guildSnowflake).block(Duration.ofSeconds(10));
            if (guild == null) {
                LOGGER.warn("Guild with ID {} not found", guildWatch.guildId());
                removeGuildWatchFunction.accept(guildWatch);
                return;
            }

            var channel = guild.getChannelById(Snowflake.of(guildWatch.channelId())).block(Duration.ofSeconds(10));
            if (channel == null) {
                LOGGER.warn("Channel with ID {} not found in guild {}", guildWatch.channelId(), guildWatch.guildId());
                removeGuildWatchFunction.accept(guildWatch);
                return;
            }

            LOGGER.info("[{}] Sending guild watch {} notification to {} ({})", id, targetName, guild.getName(), channel.getName());
            var msgBuilder = MessageCreateSpec.builder()
                .addEmbed(embedSupplier.get());

            if (guildWatch.mentionUserId() != null && !guildWatch.mentionUserId().isBlank()) {
                var mention = MentionUtil.forUser(Snowflake.of(guildWatch.mentionUserId()));
                msgBuilder.content(mention);
            } else if (guildWatch.mentionRoleId() != null && !guildWatch.mentionRoleId().isBlank()) {
                var mention = MentionUtil.forRole(Snowflake.of(guildWatch.mentionRoleId()));
                msgBuilder.content(mention);
            }

            channel.getRestChannel().createMessage(msgBuilder.build().asRequest())
                .doOnError(error -> LOGGER.error("Error sending guild notification {} to guild: {}, channelId: {}", targetName, guildWatch.guildId(), channel.getId()))
                .timeout(Duration.ofSeconds(3))
                .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(1))
                    .filter(error -> error instanceof TimeoutException)
                    .onRetryExhaustedThrow((spec, signal) -> Exceptions.retryExhausted(
                        "Retries exhausted sending " + targetName + " notification to guild: " + guildWatch.guildId() + ", channelId: " + channel.getId().asString(),
                        signal.failure())))
                .onErrorResume(error -> {
                    if (Exceptions.isRetryExhausted(error)) {
                        if (error instanceof ClientException e) {
                            int code = e.getStatus().code();
                            if (code == 429) {
                                LOGGER.error("Rate limited while sending {} notification to guild: {}, channelId: {}.", targetName, guildWatch.guildId(), channel.getId());
                            } else if (code == 403 || code == 404) {
                                var cloudflareError = e.getErrorResponse()
                                    .map(r -> r.getFields().get("body"))
                                    .filter(body -> body instanceof String)
                                    .map(body -> (String) body)
                                    .map(body -> body.contains("cloudflare"))
                                    .orElse(false);
                                if (!cloudflareError) {
                                    LOGGER.error("Missing permissions while sending {} notification to guild: {}, channelId: {}. Removing watch.", targetName, guildWatch.guildId(), channel.getId());
                                    removeGuildWatchFunction.accept(guildWatch);
                                }
                            }
                        }
                    }
                    LOGGER.error("Error sending guild notification {} to guild: {}, channelId: {}", targetName, guildWatch.guildId(), channel.getId(), error);
                    return Mono.empty();
                })
                .block(Duration.ofSeconds(10));
        } catch (Exception e) {
            LOGGER.error("Error sending guild notification {} to guild: {}, channelId: {}", targetName, guildWatch.guildId(), guildWatch.channelId(), e);
        }
    }

    public EmbedCreateSpec joinsWatchEmbed(
        final ConnectionsRecord connection
    ) {
        var profile = new ProfileDataImpl(connection.playerName(), connection.playerUuid());
        EmbedCreateSpec embed = EmbedCreateSpec.builder()
            .title("Watched Player Online")
            .addField("Player", "[" + profile.name() + "](" + profile.getNameMCLink(profile.uuid()) + ")", true)
            .addField("\u200B", "\u200B", true)
            .addField("\u200B", "\u200B", true)
            .thumbnail(profile.getAvatarURL())
            .timestamp(connection.time().toInstant())
            .color(Color.SEA_GREEN)
            .build();
        return embed;
    }

    public EmbedCreateSpec leavesWatchEmbed(
        final ConnectionsRecord connection
    ) {
        var profile = new ProfileDataImpl(connection.playerName(), connection.playerUuid());
        EmbedCreateSpec embed = EmbedCreateSpec.builder()
            .title("Watched Player Offline")
            .addField("Player", "[" + profile.name() + "](" + profile.getNameMCLink(profile.uuid()) + ")", true)
            .addField("\u200B", "\u200B", true)
            .addField("\u200B", "\u200B", true)
            .thumbnail(profile.getAvatarURL())
            .timestamp(connection.time().toInstant())
            .color(Color.RUBY)
            .build();
        return embed;
    }

    public EmbedCreateSpec chatsWatchEmbed(
        final ChatsRecord chat
    ) {
        var profile = new ProfileDataImpl(chat.playerName(), chat.playerUuid());
        EmbedCreateSpec embed = EmbedCreateSpec.builder()
            .title("Watched Player Chat")
            .addField("Player", "[" + profile.name() + "](" + profile.getNameMCLink(profile.uuid()) + ")", true)
            .addField("\u200B", "\u200B", true)
            .addField("\u200B", "\u200B", true)
            .addField("Message", escape(chat.chat()), false)
            .thumbnail(profile.getAvatarURL())
            .timestamp(chat.time().toInstant())
            .color(chat.chat().startsWith(">") ? Color.MEDIUM_SEA_GREEN : Color.SEA_GREEN)
            .build();
        return embed;
    }

    public EmbedCreateSpec chatsKeywordWatchEmbed(
        final ChatsRecord chat,
        final String keyword
    ) {
        var profile = new ProfileDataImpl(chat.playerName(), chat.playerUuid());
        EmbedCreateSpec embed = EmbedCreateSpec.builder()
            .title("Watched Keyword in Chat")
            .addField("Player", "[" + profile.name() + "](" + profile.getNameMCLink(profile.uuid()) + ")", true)
            .addField("\u200B", "\u200B", true)
            .addField("\u200B", "\u200B", true)
            .addField("Message", escape(chat.chat()), false)
            .addField("Keyword", escape(keyword), false)
            .thumbnail(profile.getAvatarURL())
            .timestamp(chat.time().toInstant())
            .color(chat.chat().startsWith(">") ? Color.MEDIUM_SEA_GREEN : Color.SEA_GREEN)
            .build();
        return embed;
    }

    public EmbedCreateSpec deathsWatchEmbed(
        final DeathsRecord death
    ) {
        var profile = new ProfileDataImpl(death.victimPlayerName(), death.victimPlayerUuid());
        var embed = EmbedCreateSpec.builder()
            .title("Watched Player Death")
            .addField("Victim", "[" + profile.name() + "](" + profile.getNameMCLink(profile.uuid()) + ")", true)
            .addField("\u200B", "\u200B", true)
            .addField("\u200B", "\u200B", true);
        if (death.killerPlayerUuid() != null) {
            var killerProfile = new ProfileDataImpl(death.killerPlayerName(), death.killerPlayerUuid());
            embed
                .addField("Killer", "[" + killerProfile.name() + "](" + killerProfile.getNameMCLink(killerProfile.uuid()) + ")", true)
                .addField("\u200B", "\u200B", true)
                .addField("\u200B", "\u200B", true);
        }
        embed
            .addField("Death Message", escape(death.deathMessage()).replace(escape(death.victimPlayerName()), "**" + escape(death.victimPlayerName()) + "**"), false)
            .thumbnail(profile.getAvatarURL())
            .timestamp(death.time().toInstant())
            .color(Color.RUBY)
            .build();
        return embed.build();
    }

    public EmbedCreateSpec killsWatchEmbed(
        final DeathsRecord death
    ) {
        var killerProfile = new ProfileDataImpl(death.killerPlayerName(), death.killerPlayerUuid());
        var victimProfile = new ProfileDataImpl(death.victimPlayerName(), death.victimPlayerUuid());
        return EmbedCreateSpec.builder()
            .title("Watched Player Kill")
            .addField("Killer", "[" + killerProfile.name() + "](" + killerProfile.getNameMCLink(killerProfile.uuid()) + ")", true)
            .addField("\u200B", "\u200B", true)
            .addField("\u200B", "\u200B", true)
            .addField("Victim", "[" + victimProfile.name() + "](" + victimProfile.getNameMCLink(victimProfile.uuid()) + ")", true)
            .addField("\u200B", "\u200B", true)
            .addField("\u200B", "\u200B", true)
            .addField("Death Message", escape(death.deathMessage()).replace(escape(killerProfile.name()), "**" + escape(killerProfile.name()) + "**"), false)
            .thumbnail(killerProfile.getAvatarURL())
            .timestamp(death.time().toInstant())
            .color(Color.SEA_GREEN)
            .build();
    }

    private void connectionsTopicListener(final String msg) {
        try {
            var data = objectMapper.readValue(msg, ConnectionsRecord.class);
            if (data.connection() == Connectiontype.JOIN) {
                joinsQueue.add(data);
            } else if (data.connection() == Connectiontype.LEAVE) {
                leavesQueue.add(data);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse ConnectionsRecord from Redis message: {}", msg, e);
        }
    }

    private void chatsTopicListener(final String msg) {
        try {
            var data = objectMapper.readValue(msg, ChatsRecord.class);
            chatsQueue.add(data);
            chatsKeywordQueue.add(data);
        } catch (Exception e) {
            LOGGER.error("Failed to parse ChatsRecord from Redis message: {}", msg, e);
        }
    }

    private void deathsTopicListener(final String msg) {
        try {
            var data = objectMapper.readValue(msg, DeathsRecord.class);
            deathsQueue.add(data);
            if (data.killerPlayerUuid() != null) {
                killsQueue.add(data);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse DeathsRecord from Redis message: {}", msg, e);
        }
    }

    @Override
    public void destroy() throws Exception {
        LOGGER.info("Shutting down watch topics");
        try {
            connectionsTopic.removeListener(connectionsTopicId);
        } catch (Exception e) {
            LOGGER.error("Failed to remove Redis topic listener: {}", connectionsTopicId, e);
        }
        try {
            chatsTopic.removeListener(chatsTopicId);
        } catch (Exception e) {
            LOGGER.error("Failed to remove Redis topic listener: {}", chatsTopicId, e);
        }
        try {
            deathsTopic.removeListener(deathsTopicId);
        } catch (Exception e) {
            LOGGER.error("Failed to remove Redis topic listener: {}", deathsTopicId, e);
        }
    }

    public void onAllGuildsLoaded(final List<UserGuildData> guilds) {
        // todo: remove guild watches for guilds that we are no longer in
    }

    public void removeWatchesInGuild(final String guildId) {
        guildPlayerWatchRepository.deleteByGuildId(guildId);
        guildChatWatchRepository.deleteByGuildId(guildId);
    }
}
