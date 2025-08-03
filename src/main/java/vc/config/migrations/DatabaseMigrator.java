package vc.config.migrations;

import org.jdbi.v3.core.Jdbi;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class DatabaseMigrator {
    private final List<DatabaseMigration> migrations = List.of(
        new V0Migration(),
        new V1Migration(),
        new V2Migration(),
        new V3Migration()
    );

    public void migrate(Path dbPath, Jdbi connection) {
        try {
            for (int i = 0; i < migrations.size(); i++) {
                DatabaseMigration migration = migrations.get(i);
                if (migration.shouldMigrate(connection)) {
                    migration.doMigration(connection);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to migrate database at " + dbPath, e);
        }
    }
}
