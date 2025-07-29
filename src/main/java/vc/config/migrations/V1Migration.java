package vc.config.migrations;

import org.jdbi.v3.core.Jdbi;

public class V1Migration implements DatabaseMigration {
    @Override
    public boolean shouldMigrate(final Jdbi connection) {
        int version = getVersion(connection);
        return version == 0;
    }

    @Override
    public void doMigration(final Jdbi connection) {
        try (var handle = connection.open()) {
            handle.createUpdate("""
                CREATE TABLE live_feed_config (\
                    guild_id INTEGER PRIMARY KEY,\
                    guild_name TEXT,\
                    live_chat_enabled INTEGER,\
                    live_chat_channel_id TEXT,\
                    live_connections_enabled INTEGER,\
                    live_connections_channel_id TEXT
                );
                """
            ).execute();
        }
        updateVersion(connection, 1);
    }
}
