package com.memme.service.store.storage;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "app.menu.storage.type", havingValue = "s3")
public class MenuImageStorageConfig {

    @Bean(destroyMethod = "close")
    public S3MenuImageStorage s3MenuImageStorage(
            @Value("${app.menu.storage.s3.bucket}") String bucket,
            @Value("${app.menu.storage.s3.region}") String region,
            @Value("${app.menu.storage.s3.endpoint:}") String endpoint,
            @Value("${app.menu.storage.s3.path-style-access:false}") boolean pathStyleAccess) {
        S3Configuration configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccess)
                .build();
        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .serviceConfiguration(configuration);
        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .serviceConfiguration(configuration);
        if (endpoint != null && !endpoint.isBlank()) {
            URI endpointUri = URI.create(endpoint);
            clientBuilder.endpointOverride(endpointUri);
            presignerBuilder.endpointOverride(endpointUri);
        }
        S3Client s3Client = clientBuilder.build();
        try {
            return new S3MenuImageStorage(s3Client, presignerBuilder.build(), bucket);
        } catch (RuntimeException exception) {
            s3Client.close();
            throw exception;
        }
    }
}
