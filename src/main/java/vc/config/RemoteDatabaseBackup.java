package vc.config;

import io.minio.*;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.StreamSupport;

@Component
public class RemoteDatabaseBackup implements DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger("RemoteDatabaseBackup");
    private final String bucketName;
    private final MinioClient minioClient;

    public RemoteDatabaseBackup(
        @Value("${BUCKET_URL}") final String bucketUrl,
        @Value("${AWS_ACCESS_KEY_ID}") final String awsAccessKeyId,
        @Value("${AWS_SECRET_ACCESS_KEY}") final String awsSecretAccessKey,
        @Value("${BUCKET_NAME}") final String bucketName
    ) {
        this.bucketName = bucketName;
        minioClient = MinioClient.builder()
            .endpoint(bucketUrl)
            .credentials(awsAccessKeyId, awsSecretAccessKey)
            .build();
    }

    public void uploadDatabaseBackup(final String backupPath) {
        Path path = Paths.get(backupPath);
        try (var fileStream = Files.newInputStream(path)) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(backupPath)
                    .stream(fileStream, Files.size(path), -1)
                    .build());
            LOGGER.info("Uploaded database backup: {}", backupPath);
        } catch (final Exception e) {
            LOGGER.error("Error uploading database backup: {}", backupPath, e);
        }
    }

    public void syncFromRemote() {
        try {
            var path = findLatestDatabaseBackup();
            downloadDatabaseBackup(path);
        } catch (final Exception e) {
            LOGGER.error("Error syncing database from remote", e);
        }
    }

    public String findLatestDatabaseBackup() {
        try {
            var backupPath = StreamSupport.stream(
                    minioClient.listObjects(
                            ListObjectsArgs.builder()
                                .bucket(bucketName)
                                .prefix("backups/config-backup-")
                                .build())
                        .spliterator(), false)
                .map(RemoteDatabaseBackup::retrieveS3ItemData)
                .filter(i -> !i.isDir())
                .sorted((o1, o2) -> o2.lastModified().compareTo(o1.lastModified()))
                .map(Item::objectName)
                .findFirst()
                .orElseThrow();
            LOGGER.info("Found latest database backup: {}", backupPath);
            return backupPath;
        } catch (final Exception e) {
            LOGGER.error("Error finding latest database backup", e);
            throw e;
        }
    }

    private static Item retrieveS3ItemData(Result<Item> r) {
        try {
            return r.get();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void downloadDatabaseBackup(final String backupPath) {
        try {
            var out = Paths.get("config.db");
            Files.deleteIfExists(out);
            var data = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(backupPath)
                    .build());
            try (var outStream = Files.newOutputStream(out)) {
                outStream.write(data.readAllBytes());
            }
            LOGGER.info("Downloaded database backup: {}", backupPath);
        } catch (final Exception e) {
            LOGGER.error("Error downloading database backup: {}", backupPath, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void destroy() {
        LOGGER.info("Shutting down RemoteDatabaseBackup");
        try {
            minioClient.close();
        } catch (final Exception e) {
            LOGGER.error("Error closing Minio client", e);
        }
    }
}
