package vc;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.sqlite3.SQLitePlugin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import vc.config.ConfigDatabase;
import vc.config.live_feed.LiveFeedConfigRecord;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;

public class LegacyDBMigration {
//    @Test
    public void migrate() {
        SpringApplication app = new SpringApplicationBuilder(Application.class)
            .registerShutdownHook(true)
            .build();
        ConfigurableApplicationContext run = app.run();
        ConfigDatabase newDatabase = run.getBean(ConfigDatabase.class);

        Connection oldDbConnection;
        try {
            final Path dbPath = Paths.get("guild-config.db");
            oldDbConnection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        var oldJdbi = Jdbi.create(oldDbConnection);
        new SQLitePlugin().customizeJdbi(oldJdbi);
        oldJdbi.registerRowMapper(ConstructorMapper.factory(LiveFeedConfigRecord.class));

        try (var oldDbHandle = oldJdbi.open()) {
            oldDbHandle.select("SELECT * FROM guild_config")
                .mapTo(LiveFeedConfigRecord.class)
                .stream()
                .forEach(newDatabase::writeGuildConfigRecord);
        }
        run.close();
    }
}
