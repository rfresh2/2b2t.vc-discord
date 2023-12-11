package vc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@Component
public class GuildConfigDatabase {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildConfigDatabase.class);
    // backups older than this date will be deleted
    private static final Duration ROLLING_BACKUP_DURATION = Duration.ofDays(7);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withLocale(Locale.US).withZone(
        ZoneId.of("America/Los_Angeles"));
    private final Path backupPath = Paths.get("backups");
    private final Connection connection;
    private final RemoteDatabaseBackup remoteDatabaseBackup;
    // special mode where we sync from remote and don't upload backups.
    // intended for syncing to remote state on a new server or a local dev machine
    private final boolean dbSync;

    public GuildConfigDatabase(
        final RemoteDatabaseBackup remoteDatabaseBackup,
        @Value("${DB_SYNC}") final String dbSync
    ) {
        this.dbSync = Boolean.parseBoolean(dbSync);
        this.remoteDatabaseBackup = remoteDatabaseBackup;
        if (this.dbSync) this.remoteDatabaseBackup.syncFromRemote();
        try {
            final Path dbPath = Paths.get("guild-config.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            createGuildConfigTable();
        } catch (final Exception e) {
            LOGGER.error("Error initializing guild config database connection", e);
            throw new RuntimeException(e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    private void close() {
        try {
            connection.close();
        } catch (final Exception e) {
            LOGGER.error("Error closing guild config database connection", e);
        }
    }

    public void backupDatabase() {
        try {
            if (!backupPath.toFile().exists()) {
                backupPath.toFile().mkdirs();
            }
            var backupPath = "backups/guild-config-backup-" + DATE_FORMATTER.format(Instant.now()) + ".db";
            connection.createStatement().executeUpdate("BACKUP TO '" + backupPath + "'");
            if (!dbSync) remoteDatabaseBackup.uploadDatabaseBackup(backupPath);
        } catch (final Exception e) {
            LOGGER.error("Error backing up guild config database", e);
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
                if (file.getName().startsWith("guild-config-backup-")) {
                    final String dateString = file.getName().substring("guild-config-backup-".length(), "guild-config-backup-".length() + "yyyy-MM-dd-HH-mm-ss".length());
                    final Instant date = Instant.from(DATE_FORMATTER.parse(dateString));
                    if (date.isBefore(Instant.now().minus(ROLLING_BACKUP_DURATION))) {
                        LOGGER.info("Deleting old guild config database backup {}", file.getName());
                        if (!file.delete()) {
                            LOGGER.warn("Failed to delete old guild config database backup {}", file.getName());
                        }
                    }
                }
            }
            LOGGER.info("Completed cleaning old backups");
        } catch (final Exception e) {
            LOGGER.error("Error cleaning old guild config database backups", e);
        }
    }

    private void createGuildConfigTable() {
        try {
            connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS guild_config ("
                                                           + "guild_id INTEGER, "
                                                           + "guild_name TEXT, "
                                                           + "live_chat_enabled INTEGER, "
                                                           + "live_chat_channel_id TEXT, "
                                                           + "live_connections_enabled INTEGER, "
                                                           + "live_connections_channel_id TEXT"
                                                           + ")");
            connection.createStatement().executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS unique_guild_id ON guild_config (guild_id)");
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<GuildConfigRecord> getGuildConfigRecord(final String guildId) {
        try {
            ResultSet resultSet = connection.createStatement()
                .executeQuery("SELECT * FROM guild_config WHERE guild_id = " + guildId);
            if (resultSet.next()) {
                return Optional.of(new GuildConfigRecord(
                    resultSet.getString("guild_id"),
                    resultSet.getString("guild_name"),
                    resultSet.getBoolean("live_chat_enabled"),
                    resultSet.getString("live_chat_channel_id"),
                    resultSet.getBoolean("live_connections_enabled"),
                    resultSet.getString("live_connections_channel_id")
                ));
            }
        } catch (final Exception e) {
            LOGGER.error("Error getting guild config record for guild {}", guildId, e);
        }
        return Optional.empty();
    }

    public void writeGuildConfigRecord(final GuildConfigRecord config) {
        try {
            PreparedStatement statement = connection.prepareStatement("INSERT OR REPLACE INTO guild_config VALUES (?, ?, ?, ?, ?, ?)");
            statement.setString(1, config.guildId());
            statement.setString(2, config.guildName());
            statement.setBoolean(3, config.liveChatEnabled());
            statement.setString(4, config.liveChatChannelId());
            statement.setBoolean(5, config.liveConnectionsEnabled());
            statement.setString(6, config.liveConnectionsChannelId());
            statement.executeUpdate();
        } catch (final Exception e) {
            LOGGER.error("Error writing guild config record for guild {}", config.guildId(), e);
        }
    }
}
