package vc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public class GuildConfigDatabase {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildConfigDatabase.class);
    private final Connection connection;

    public GuildConfigDatabase() {
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
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withLocale(Locale.US).withZone(
                ZoneId.of("America/Los_Angeles"));
            final Path backupPath = Paths.get("backups");
            if (!backupPath.toFile().exists()) {
                backupPath.toFile().mkdirs();
            }
            connection.createStatement().executeUpdate("BACKUP TO 'backups/guild-config-backup-" + formatter.format(Instant.now()) + ".db'");
        } catch (final Exception e) {
            LOGGER.error("Error backing up guild config database", e);
        }
    }

    private void createGuildConfigTable() {
        try {
            connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS guild_config (guild_id INTEGER, guild_name TEXT, live_chat_enabled INTEGER, live_chat_channel_id TEXT)");
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
                    resultSet.getString("live_chat_channel_id")
                ));
            }
        } catch (final Exception e) {
            LOGGER.error("Error getting guild config record for guild {}", guildId, e);
        }
        return Optional.empty();
    }

    public void writeGuildConfigRecord(final GuildConfigRecord config) {
        try {
            PreparedStatement statement = connection.prepareStatement("INSERT OR REPLACE INTO guild_config VALUES (?, ?, ?, ?)");
            statement.setString(1, config.guildId());
            statement.setString(2, config.guildName());
            statement.setBoolean(3, config.liveChatEnabled());
            statement.setString(4, config.liveChatChannelId());
            statement.executeUpdate();
        } catch (final Exception e) {
            LOGGER.error("Error writing guild config record for guild {}", config.guildId(), e);
        }
    }
}
