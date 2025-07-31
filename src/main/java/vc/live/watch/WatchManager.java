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
import vc.config.watch.GuildWatchConfigRecord;
import vc.config.watch.UserWatchConfigRecord;
import vc.config.watch.WatchConfigManager;
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
import java.util.function.Function;

@Component
public class WatchManager implements DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchManager.class);
    private final WatchConfigManager watchConfigManager;
    private final RedisClient redisClient;
    private final GatewayDiscordClient discordClient;
    private final ObjectMapper objectMapper;
    private final boolean watchesEnabled;
    private final ConcurrentLinkedDeque<ConnectionsRecord> joinsQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<ConnectionsRecord> leavesQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<ChatsRecord> chatsQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<DeathsRecord> deathsQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<DeathsRecord> killsQueue = new ConcurrentLinkedDeque<>();
    RReliableTopic connectionsTopic;
    RReliableTopic chatsTopic;
    RReliableTopic deathsTopic;
    String connectionsTopicId;
    String chatsTopicId;
    String deathsTopicId;

    public WatchManager(
        final WatchConfigManager watchConfigManager,
        final RedisClient redisClient,
        final GatewayDiscordClient discordClient,
        final ObjectMapper objectMapper,
        @Value("${WATCHES}")
        final String watchesEnabled
    ) {
        this.watchConfigManager = watchConfigManager;
        this.redisClient = redisClient;
        this.discordClient = discordClient;
        this.objectMapper = objectMapper;
        this.watchesEnabled = Boolean.parseBoolean(watchesEnabled);
        if (this.watchesEnabled) {
            LOGGER.info("Watch manager enabled");
            watchConfigManager.loadUserWatchConfigs();
            LOGGER.info("Loaded {} user watch configs", watchConfigManager.getAllUserWatchConfigs().size());
            watchConfigManager.loadGuildWatchConfigs();
            LOGGER.info("Loaded {} guild watch configs", watchConfigManager.getAllGuildWatchConfigs().size());
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

    private <T> void processQueue(
        String id,
        ConcurrentLinkedDeque<T> queue,
        Function<T, EmbedCreateSpec> watchEmbedProvider,
        Function<T, UUID> targetUuidProvider,
        Function<T, String> targetNameProvider,
        BiFunction<T, UserWatchConfigRecord, Boolean> userActivePredicate,
        BiFunction<T, GuildWatchConfigRecord, Boolean> guildActivePredicate
    ) {
        try {
            while (!queue.isEmpty()) {
                var data = queue.poll();
                if (data == null) continue;
                var userWatches = watchConfigManager.getUserWatches(targetUuidProvider.apply(data));
                for (var userWatch : userWatches) {
                    if (!userActivePredicate.apply(data, userWatch)) continue;
                    try {
                        var ownerUserId = userWatch.ownerUserId();
                        var channel = discordClient.getUserById(Snowflake.of(ownerUserId))
                            .doOnSuccess(user -> LOGGER.info("[{}] Sending {} user watch to {}", id, targetNameProvider.apply(data), user.getUsername()))
                            .flatMap(User::getPrivateChannel)
                            .block(Duration.ofSeconds(10));
                        var msg = MessageCreateSpec.builder()
                            .addEmbed(watchEmbedProvider.apply(data))
                            .build();
                        channel.createMessage(msg)
                            .doOnError(error -> LOGGER.error("Error sending user watch to: {}", ownerUserId))
                            .timeout(Duration.ofSeconds(3))
                            .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(1))
                                .filter(error -> error instanceof TimeoutException)
                                .onRetryExhaustedThrow((spec, signal) -> Exceptions.retryExhausted(
                                    "Retries exhausted sending watch to user: " + ownerUserId + ", channelId: " + channel.getId().asString(),
                                    signal.failure())))
                            .onErrorResume(error -> {
                                if (Exceptions.isRetryExhausted(error)) {
                                    if (error instanceof ClientException e) {
                                        int code = e.getStatus().code();
                                        if (code == 429) {
                                            LOGGER.error("Rate limited while broadcasting watch to user: {}", ownerUserId);
                                        } else if (code == 403 || code == 404) {
                                            LOGGER.error("Missing permissions while broadcasting watch to user: {}. Removing watch.", ownerUserId);
                                            watchConfigManager.removeUserWatchConfig(userWatch);
                                        }
                                    }
                                }
                                LOGGER.error("Error sending user watch to: {}", ownerUserId, error);
                                return Mono.empty();
                            })
                            .block(Duration.ofSeconds(10));
                    } catch (Exception e) {
                        LOGGER.error("Error sending user watch notification {}", userWatch, e);
                    }
                }
                for (var userWatch : userWatches) {
                    if (!targetNameProvider.apply(data).equals(userWatch.targetName())) {
                        LOGGER.info("Updating user watch {} target name from {} to {}", userWatch.watchId(), userWatch.targetName(), targetNameProvider.apply(data));
                        var newConfig = new UserWatchConfigRecord(
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
                        watchConfigManager.updateUserWatchConfig(newConfig);
                    }
                }
                var guildWatches = watchConfigManager.getGuildWatches(targetUuidProvider.apply(data));
                for (var guildWatch : guildWatches) {
                    if (!guildActivePredicate.apply(data, guildWatch)) continue;
                    try {
                        var guildId = Snowflake.of(guildWatch.guildId());
                        var guild = discordClient.getGuildById(guildId).block(Duration.ofSeconds(10));
                        if (guild == null) {
                            LOGGER.warn("Guild with ID {} not found for watch {}", guildId.asString(), guildWatch);
                            watchConfigManager.removeGuildWatchConfig(guildWatch);
                            continue;
                        }
                        var channel = guild.getChannelById(Snowflake.of(guildWatch.channelId())).block(Duration.ofSeconds(10));
                        if (channel == null) {
                            LOGGER.warn("Channel with ID {} not found in guild {}", guildWatch.channelId(), guildId.asString());
                            watchConfigManager.removeGuildWatchConfig(guildWatch);
                            continue;
                        }
                        LOGGER.info("[{}] Sending {} guild watch to {} ({})", id, targetNameProvider.apply(data), guild.getName(), channel.getName());
                        var msgBuilder = MessageCreateSpec.builder()
                            .addEmbed(watchEmbedProvider.apply(data));
                        if (guildWatch.mentionUserId() != null && !guildWatch.mentionUserId().isBlank()) {
                            var mention = MentionUtil.forUser(Snowflake.of(guildWatch.mentionUserId()));
                            msgBuilder.content(mention);
                        } else if (guildWatch.mentionRoleId() != null && !guildWatch.mentionRoleId().isBlank()) {
                            var mention = MentionUtil.forRole(Snowflake.of(guildWatch.mentionRoleId()));
                            msgBuilder.content(mention);
                        }
                        channel.getRestChannel().createMessage(msgBuilder.build().asRequest())
                            .doOnError(error -> LOGGER.error("Error sending guild watch to guild: {}, channelId: {}", guildId, channel.getId()))
                            .timeout(Duration.ofSeconds(3))
                            .retryWhen(Retry.fixedDelay(1, Duration.ofSeconds(1))
                                .filter(error -> error instanceof TimeoutException)
                                .onRetryExhaustedThrow((spec, signal) -> Exceptions.retryExhausted(
                                    "Retries exhausted sending watch to guild: " + guildId + ", channelId: " + channel.getId().asString(),
                                    signal.failure())))
                            .onErrorResume(error -> {
                                if (Exceptions.isRetryExhausted(error)) {
                                    if (error instanceof ClientException e) {
                                        int code = e.getStatus().code();
                                        if (code == 429) {
                                            LOGGER.error("Rate limited while broadcasting watch to guild: {}, channelId: {}.", guildId, channel.getId());
                                        } else if (code == 403 || code == 404) {
                                            LOGGER.error("Missing permissions while broadcasting watch to guild: {}, channelId: {}. Removing watch.", guildId, channel.getId());
                                            watchConfigManager.removeGuildWatchConfig(guildWatch);
                                        }
                                    }
                                }
                                LOGGER.error("Error sending guild watch to guild: {}, channelId: {}", guildId, channel.getId(), error);
                                return Mono.empty();
                            })
                            .block(Duration.ofSeconds(10));
                    } catch (Exception e) {
                        LOGGER.error("Error sending guild watch notification {}", guildWatch, e);
                    }
                }
                for (var guildWatch : guildWatches) {
                    if (!targetNameProvider.apply(data).equals(guildWatch.targetName())) {
                        LOGGER.info("Updating guild watch {} target name from {} to {}", guildWatch.watchId(), guildWatch.targetName(), targetNameProvider.apply(data));
                        var newConfig = new GuildWatchConfigRecord(
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
                        watchConfigManager.updateGuildWatchConfig(newConfig);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error while processing {} queue", id, e);
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
            .addField("Death Message", escape(death.deathMessage().replace(death.victimPlayerName(), "**" + death.victimPlayerName() + "**")), false)
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
            .addField("Death Message", escape(death.deathMessage().replace(killerProfile.name(), "**" + killerProfile.name() + "**")), false)
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
        watchConfigManager.removeGuildWatchConfigs(guildId);
    }

    String escape(String message) {
        return message.replaceAll("_", "\\\\_");
    }
}
