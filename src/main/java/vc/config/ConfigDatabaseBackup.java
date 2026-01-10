package vc.config;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class ConfigDatabaseBackup implements DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigDatabaseBackup.class);
    private static final Duration ROLLING_BACKUP_DURATION = Duration.ofDays(7);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withLocale(
        Locale.US).withZone(
        ZoneId.of("America/Los_Angeles"));

    private final Connection connection;
    private final Jdbi jdbi;
    private final RemoteDatabaseBackup remoteDatabaseBackup;
    private final boolean dbRemoteBackup;

    private final Path backupPath = Paths.get("backups");

    public ConfigDatabaseBackup(
        final Connection connection,
        final Jdbi jdbi,
        final RemoteDatabaseBackup remoteDatabaseBackup,
        @Value("${DB_REMOTE_BACKUP}") final String dbRemoteBackup
    ) {
        this.connection = connection;
        this.jdbi = jdbi;
        this.remoteDatabaseBackup = remoteDatabaseBackup;
        this.dbRemoteBackup = Boolean.parseBoolean(dbRemoteBackup);
    }

    @Override
    public void destroy() {
        LOGGER.info("Shutting down ConfigDatabase");
        backupDatabase();
    }

    @Scheduled(fixedRate = 24, initialDelay = 0, timeUnit = TimeUnit.HOURS)
    public void scheduledBackup() {
        backupDatabase();
    }

    public void backupDatabase() {
        try {
            if (!backupPath.toFile().exists()) {
                backupPath.toFile().mkdirs();
            }
            var backupPath = "backups/config-backup-" + DATE_FORMATTER.format(Instant.now()) + ".db";
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("BACKUP TO '" + backupPath + "'");
            }
            if (dbRemoteBackup) {
                remoteDatabaseBackup.uploadDatabaseBackup(backupPath);
            }
        } catch (final Exception e) {
            LOGGER.error("Error backing up config database", e);
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
                if (file.getName().startsWith("config-backup-")) {
                    final String dateString = file.getName().substring("config-backup-".length(), "config-backup-".length() + "yyyy-MM-dd-HH-mm-ss".length());
                    final Instant date = Instant.from(DATE_FORMATTER.parse(dateString));
                    if (date.isBefore(Instant.now().minus(ROLLING_BACKUP_DURATION))) {
                        LOGGER.info("Deleting old config database backup {}", file.getName());
                        if (!file.delete()) {
                            LOGGER.warn("Failed to delete old config database backup {}", file.getName());
                        }
                    }
                }
            }
            LOGGER.info("Completed cleaning old backups");
        } catch (final Exception e) {
            LOGGER.error("Error cleaning old config database backups", e);
        }
    }
}
