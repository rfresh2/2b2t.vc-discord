package vc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.nio.file.Paths;

@Component
public class RemoteDatabaseBackup {
    private static final Logger LOGGER = LoggerFactory.getLogger("RemoteDatabaseBackup");
    private final S3Client s3Client;
    private final String bucketName;

    public RemoteDatabaseBackup(
        @Value("${BUCKET_URL}") final String bucketUrl,
        @Value("${AWS_ACCESS_KEY_ID}") final String awsAccessKeyId,
        @Value("${AWS_SECRET_ACCESS_KEY}") final String awsSecretAccessKey,
        @Value("${BUCKET_NAME}") final String bucketName) {
        this.bucketName = bucketName;
        s3Client = S3Client.builder()
            .endpointOverride(URI.create(bucketUrl))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)))
            .build();
    }

    public void uploadDatabaseBackup(final String backupPath) {
        try {
            s3Client.putObject(builder -> builder.bucket(bucketName).key(backupPath).build(), Paths.get(backupPath));
            LOGGER.info("Uploaded database backup: {}", backupPath);
        } catch (final Exception e) {
            LOGGER.error("Error uploading database backup: {}", backupPath, e);
        }
    }
}
