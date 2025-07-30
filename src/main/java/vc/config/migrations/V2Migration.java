package vc.config.migrations;

import org.jdbi.v3.core.Jdbi;

public class V2Migration implements DatabaseMigration {
    @Override
    public boolean shouldMigrate(final Jdbi connection) {
        return getVersion(connection) == 1;
    }

    @Override
    public void doMigration(final Jdbi connection) {
        try (var handle = connection.open()) {
            handle.createUpdate("""
                CREATE TABLE user_watch_config (\
                    watch_id TEXT PRIMARY KEY,\
                    owner_user_id TEXT,\
                    owner_user_name TEXT,\
                    joins INTEGER,\
                    leaves INTEGER,\
                    chats INTEGER,\
                    deaths INTEGER,\
                    kills INTEGER,\
                    target_uuid TEXT,\
                    target_name TEXT
                );
                """)
                .execute();
            handle.createUpdate("""
                CREATE TABLE guild_watch_config (\
                    watch_id TEXT PRIMARY KEY,\
                    guild_id TEXT,\
                    guild_name TEXT,\
                    channel_id TEXT,\
                    joins INTEGER,\
                    leaves INTEGER,\
                    chats INTEGER,\
                    deaths INTEGER,\
                    kills INTEGER,\
                    mention_user_id TEXT,\
                    mention_role_id TEXT,\
                    target_uuid TEXT,\
                    target_name TEXT
                );
                """)
                .execute();
        }
        updateVersion(connection, 2);
    }
}
