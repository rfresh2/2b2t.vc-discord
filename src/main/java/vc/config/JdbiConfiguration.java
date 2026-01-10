package vc.config;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.sqlite3.SQLitePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vc.config.live_feed.model.LiveFeedConfig;
import vc.config.migrations.DatabaseMigrator;
import vc.config.watch.model.GuildChatWatchConfig;
import vc.config.watch.model.GuildPlayerWatchConfig;
import vc.config.watch.model.UserChatWatchConfig;
import vc.config.watch.model.UserPlayerWatchConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;

@Configuration
public class JdbiConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdbiConfiguration.class);

    @Bean
    public Connection connection() {
        final Path dbPath = Paths.get("config.db");
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        } catch (final Exception e) {
            LOGGER.error("Error initializing config database connection", e);
            throw new RuntimeException(e);
        }
    }

    @Bean
    public Jdbi jdbi(
        DatabaseMigrator migrator,
        RemoteDatabaseBackup remoteDatabaseBackup,
        @Value("${DB_REMOTE_DOWNLOAD}") final String dbRemoteDownload,
        Connection connection
    ) {
        if (Boolean.parseBoolean(dbRemoteDownload)) {
            remoteDatabaseBackup.syncFromRemote();
        }
        Jdbi jdbi = Jdbi.create(connection);
        new SQLitePlugin().customizeJdbi(jdbi);
        jdbi.registerRowMapper(ConstructorMapper.factory(LiveFeedConfig.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(UserPlayerWatchConfig.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(GuildPlayerWatchConfig.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(UserChatWatchConfig.class));
        jdbi.registerRowMapper(ConstructorMapper.factory(GuildChatWatchConfig.class));
        final Path dbPath = Paths.get("config.db");
        migrator.migrate(dbPath, jdbi);
        return jdbi;
    }
}
