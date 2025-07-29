package vc.config.migrations;

import org.jdbi.v3.core.Jdbi;

import java.sql.SQLException;

public interface DatabaseMigration {
    boolean shouldMigrate(Jdbi connection);
    void doMigration(Jdbi connection);

    default boolean tableExists(String tableName, Jdbi connection) throws SQLException {
        try (var handle = connection.open()) {
            return handle.select("SELECT name from sqlite_master where type='table' and name=:name")
                .bind("name", tableName)
                .mapTo(String.class)
                .findFirst()
                .isPresent();
        }
    }

    default int getVersion(Jdbi connection) {
        try (var handle = connection.open()) {
            return handle.select("SELECT value from metadata where key = :key")
                .bind("key", "version")
                .mapTo(Integer.class)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unable to get version from database"));
        }
    }

    default void updateVersion(Jdbi connection, int version) {
        try (var handle = connection.open()) {
            handle.createUpdate("UPDATE metadata SET value = :version WHERE key = 'version'")
                .bind("version", version)
                .execute();
            handle.createUpdate("UPDATE metadata SET value = datetime('now') WHERE key = 'last_updated'")
                .execute();
        }
    }
}
