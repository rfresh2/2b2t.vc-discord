package vc.config.migrations;

import org.jdbi.v3.core.Jdbi;

public class V3Migration implements DatabaseMigration {
    @Override
    public boolean shouldMigrate(final Jdbi connection) {
        return getVersion(connection) == 2;
    }

    @Override
    public void doMigration(final Jdbi connection) {
        try (var handle = connection.open()) {
            handle.createUpdate("""
                ALTER TABLE user_watch_config rename to user_player_watch_config;
                """)
                .execute();
            handle.createUpdate("""
                ALTER TABLE guild_watch_config rename to guild_player_watch_config;
                """)
                .execute();
            handle.createUpdate("""
                CREATE TABLE user_chat_watch_config (\
                    watch_id TEXT PRIMARY KEY,\
                    owner_user_id TEXT,\
                    owner_user_name TEXT,\
                    keyword TEXT,\
                    case_sensitive INTEGER
                );
                """)
                .execute();
            handle.createUpdate("""
                CREATE TABLE guild_chat_watch_config (\
                    watch_id TEXT PRIMARY KEY,\
                    guild_id TEXT,\
                    guild_name TEXT,\
                    channel_id TEXT,\
                    keyword TEXT,\
                    case_sensitive INTEGER,\
                    mention_user_id TEXT,\
                    mention_role_id TEXT
                );
                """)
                .execute();
        }
        updateVersion(connection, 3);
    }
}
