package vc.config.migrations;

import org.jdbi.v3.core.Jdbi;

import java.sql.SQLException;

public class V0Migration implements DatabaseMigration {
    @Override
    public boolean shouldMigrate(final Jdbi connection) {
        try {
            return !tableExists("metadata", connection);
        } catch (SQLException e) {
            throw new RuntimeException("Error checking if metadata table exists", e);
        }
    }

    @Override
    public void doMigration(final Jdbi connection) {
        try (var handle = connection.open()) {
            handle.execute("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT);");
            handle.execute("INSERT INTO metadata (key, value) VALUES ('version', '0');");
            handle.execute("INSERT INTO metadata (key, value) VALUES ('last_updated', datetime('now'));");
        }
    }
}
