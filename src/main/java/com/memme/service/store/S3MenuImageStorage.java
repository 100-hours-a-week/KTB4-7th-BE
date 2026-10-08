package com.memme.service.store;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@ConditionalOnProperty(name = "app.sales.storage.type", havingValue = "s3")
public class S3MenuImageStorage implements MenuImageStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3MenuImageStorage(S3Client client, S3Presigner presigner,
            @Value("${app.sales.storage.s3.bucket}") String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public String store(long storeId, byte[] content, String mimeType) {
        String extension = "image/png".equals(mimeType) ? ".png" : ".jpg";
        String key = "menus/" + storeId + "/" + UUID.randomUUID() + extension;
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                .contentType(mimeType).build(), RequestBody.fromBytes(content));
        return key;
    }

    @Override
    public String signedReadUrl(String storageKey) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(storageKey).build())
                .build()).url().toExternalForm();
    }

    @Override
    public void delete(String storageKey) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
    }
}
