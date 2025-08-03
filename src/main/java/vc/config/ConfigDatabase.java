package vc.config;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.sqlite3.SQLitePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vc.config.live_feed.LiveFeedConfigRecord;
import vc.config.migrations.DatabaseMigrator;
import vc.config.watch.GuildChatWatchConfig;
import vc.config.watch.GuildPlayerWatchConfig;
import vc.config.watch.UserChatWatchConfig;
import vc.config.watch.UserPlayerWatchConfig;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class ConfigDatabase implements DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigDatabase.class);
    // backups older than this date will be deleted
    private static final Duration ROLLING_BACKUP_DURATION = Duration.ofDays(7);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withLocale(
        Locale.US).withZone(
        ZoneId.of("America/Los_Angeles"));
    private final Path backupPath = Paths.get("backups");
    private final Connection connection;
    private final RemoteDatabaseBackup remoteDatabaseBackup;
    // special mode where we sync from remote and don't upload backups.
    // intended for syncing to remote state on a new server or a local dev machine
    private final boolean dbSync;
    private final Jdbi jdbi;

    public ConfigDatabase(
        DatabaseMigrator migrator,
        final RemoteDatabaseBackup remoteDatabaseBackup,
        @Value("${DB_SYNC}") final String dbSync
    ) {
        this.dbSync = Boolean.parseBoolean(dbSync);
        this.remoteDatabaseBackup = remoteDatabaseBackup;
//        if (this.dbSync) this.remoteDatabaseBackup.syncFromRemote();
        final Path dbPath = Paths.get("config.db");
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        } catch (final Exception e) {
            LOGGER.error("Error initializing config database connection", e);
            throw new RuntimeException(e);
        }
        if (!this.dbSync) backupDatabase();
        jdbi = Jdbi.create(connection);
        new SQLitePlugin().customizeJdbi(jdbi);
        jdbi.registerRowMapper(ConstructorMapper.factory(LiveFeedConfigRecord.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(UserPlayerWatchConfig.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(GuildPlayerWatchConfig.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(UserChatWatchConfig.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(GuildChatWatchConfig.class));
        migrator.migrate(dbPath, jdbi);
    }

    @Override
    public void destroy() {
        LOGGER.info("Shutting down ConfigDatabase");
        try {
            backupDatabase();
            connection.close();
        } catch (final Exception e) {
            LOGGER.error("Error closing config database connection", e);
        }
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    public void backupDatabase() {
        try {
            if (!backupPath.toFile().exists()) {
                backupPath.toFile().mkdirs();
            }
            var backupPath = "backups/config-backup-" + DATE_FORMATTER.format(Instant.now()) + ".db";
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("BACKUP TO '" + backupPath + "'");
            }
            if (!dbSync) remoteDatabaseBackup.uploadDatabaseBackup(backupPath);
        } catch (final Exception e) {
            LOGGER.error("Error backing up config database", e);
        }
        cleanOldBackups();
    }

    private void cleanOldBackups() {
        try {
            if (!backupPath.toFile().exists()) {
                return;
            }
            File[] files = backupPath.toFile().listFiles();
            if (files == null) {
                LOGGER.warn("no backups found?");
                return;
            }
            for (final File file : files) {
                if (file.getName().startsWith("config-backup-")) {
                    final String dateString = file.getName().substring("config-backup-".length(), "config-backup-".length() + "yyyy-MM-dd-HH-mm-ss".length());
                    final Instant date = Instant.from(DATE_FORMATTER.parse(dateString));
                    if (date.isBefore(Instant.now().minus(ROLLING_BACKUP_DURATION))) {
                        LOGGER.info("Deleting old config database backup {}", file.getName());
                        if (!file.delete()) {
                            LOGGER.warn("Failed to delete old config database backup {}", file.getName());
                        }
                    }
                }
            }
            LOGGER.info("Completed cleaning old backups");
        } catch (final Exception e) {
            LOGGER.error("Error cleaning old config database backups", e);
        }
    }

    public Optional<LiveFeedConfigRecord> getLiveFeedConfigRecord(final String guildId) {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM live_feed_config WHERE guild_id = :guildId")
                .bind("guildId", guildId)
                .mapTo(LiveFeedConfigRecord.class)
                .findFirst();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving live feed config record for guild {}", guildId, e);
            return Optional.empty();
        }
    }

    public void writeGuildConfigRecord(final LiveFeedConfigRecord config) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("""
                INSERT OR REPLACE INTO live_feed_config VALUES (
                    :guildId,
                    :guildName,
                    :liveChatEnabled,
                    :liveChatChannelId,
                    :liveConnectionsEnabled,
                    :liveConnectionsChannelId)
                """)
                .bind("guildId", config.guildId())
                .bind("guildName", config.guildName())
                .bind("liveChatEnabled", config.liveChatEnabled())
                .bind("liveChatChannelId", config.liveChatChannelId())
                .bind("liveConnectionsEnabled", config.liveConnectionsEnabled())
                .bind("liveConnectionsChannelId", config.liveConnectionsChannelId())
                .execute();
        }
    }

    public List<UserPlayerWatchConfig> getUserWatchConfigs() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM user_player_watch_config")
                .mapTo(UserPlayerWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving user watch configs", e);
            return List.of();
        }
    }

    public List<GuildPlayerWatchConfig> getGuildWatchConfigs() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM guild_player_watch_config")
                .mapTo(GuildPlayerWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving guild watch configs", e);
            return List.of();
        }
    }

    public List<UserChatWatchConfig> getUserChatWatchConfigs() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM user_chat_watch_config")
                .mapTo(UserChatWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving user chat watch configs", e);
            return List.of();
        }
    }

    public List<GuildChatWatchConfig> getGuildChatWatchConfigs() {
        try (var handle = jdbi.open()) {
            return handle.select("SELECT * FROM guild_chat_watch_config")
                .mapTo(GuildChatWatchConfig.class)
                .list();
        } catch (final Exception e) {
            LOGGER.error("Error retrieving guild chat watch configs", e);
            return List.of();
        }
    }

    public void writeUserWatchConfig(final UserPlayerWatchConfig config) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("""
                INSERT OR REPLACE INTO user_player_watch_config VALUES (
                    :watchId,
                    :ownerUserId,
                    :ownerUserName,
                    :joins,
                    :leaves,
                    :chats,
                    :deaths,
                    :kills,
                    :targetUuid,
                    :targetName
                );
                """)
                .bind("watchId", config.watchId())
                .bind("ownerUserId", config.ownerUserId())
                .bind("ownerUserName", config.ownerUserName())
                .bind("joins", config.joins())
                .bind("leaves", config.leaves())
                .bind("chats", config.chats())
                .bind("deaths", config.deaths())
                .bind("kills", config.kills())
                .bind("targetUuid", config.targetUuid())
                .bind("targetName", config.targetName())
                .execute();
        }
    }

    public void writeGuildWatchConfig(final GuildPlayerWatchConfig config) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("""
                INSERT OR REPLACE INTO guild_player_watch_config VALUES (
                    :watchId,
                    :guildId,
                    :guildName,
                    :channelId,
                    :joins,
                    :leaves,
                    :chats,
                    :deaths,
                    :kills,
                    :mentionUserId,
                    :mentionRoleId,
                    :targetUuid,
                    :targetName
                );
                """)
                .bind("watchId", config.watchId())
                .bind("guildId", config.guildId())
                .bind("guildName", config.guildName())
                .bind("channelId", config.channelId())
                .bind("joins", config.joins())
                .bind("leaves", config.leaves())
                .bind("chats", config.chats())
                .bind("deaths", config.deaths())
                .bind("kills", config.kills())
                .bind("mentionUserId", config.mentionUserId())
                .bind("mentionRoleId", config.mentionRoleId())
                .bind("targetUuid", config.targetUuid())
                .bind("targetName", config.targetName())
                .execute();
        }
    }

    public void writeUserChatWatchConfig(final UserChatWatchConfig config) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("""
                INSERT OR REPLACE INTO user_chat_watch_config VALUES (
                    :watchId,
                    :ownerUserId,
                    :ownerUserName,
                    :keyword,
                    :caseSensitive
                );
                """)
                .bind("watchId", config.watchId())
                .bind("ownerUserId", config.ownerUserId())
                .bind("ownerUserName", config.ownerUserName())
                .bind("keyword", config.keyword())
                .bind("caseSensitive", config.caseSensitive())
                .execute();
        }
    }

    public void writeGuildChatWatchConfig(final GuildChatWatchConfig config) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("""
                INSERT OR REPLACE INTO guild_chat_watch_config VALUES (
                    :watchId,
                    :guildId,
                    :guildName,
                    :channelId,
                    :keyword,
                    :caseSensitive,
                    :mentionUserId,
                    :mentionRoleId
                );
                """)
                .bind("watchId", config.watchId())
                .bind("guildId", config.guildId())
                .bind("guildName", config.guildName())
                .bind("channelId", config.channelId())
                .bind("keyword", config.keyword())
                .bind("caseSensitive", config.caseSensitive())
                .bind("mentionUserId", config.mentionUserId())
                .bind("mentionRoleId", config.mentionRoleId())
                .execute();
        }
    }

    public void deleteUserWatchConfig(final String watchId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM user_player_watch_config WHERE watch_id = :watchId")
                .bind("watchId", watchId)
                .execute();
        }
    }

    public void deleteGuildWatchConfig(final String watchId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM guild_player_watch_config WHERE watch_id = :watchId")
                .bind("watchId", watchId)
                .execute();
        }
    }

    public void deleteUserChatWatchConfig(final String watchId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM user_chat_watch_config WHERE watch_id = :watchId")
                .bind("watchId", watchId)
                .execute();
        }
    }

    public void deleteGuildChatWatchConfig(final String watchId) {
        try (var handle = jdbi.open()) {
            handle.createUpdate("DELETE FROM guild_chat_watch_config WHERE watch_id = :watchId")
                .bind("watchId", watchId)
                .execute();
        }
    }
}
