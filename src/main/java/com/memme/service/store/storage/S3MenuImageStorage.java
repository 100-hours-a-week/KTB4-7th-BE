package com.memme.service.store.storage;

import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public class S3MenuImageStorage implements MenuImageStorage, AutoCloseable {

    private static final Duration READ_URL_LIFETIME = Duration.ofMinutes(10);

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3MenuImageStorage(S3Client s3Client, S3Presigner presigner, String bucket) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("menu image bucket must not be blank");
        }
        this.bucket = bucket;
    }

    @Override
    public String store(MenuImageFile image) {
        String storageKey = MenuImageStorageKey.create(image);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(image.mimeType())
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(image.content()));
            return storageKey;
        } catch (SdkException exception) {
            try {
                delete(storageKey);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new IllegalStateException("메뉴 이미지를 S3에 저장하지 못했습니다.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(MenuImageStorageKey.validate(storageKey))
                .build();
        try {
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            throw new IllegalStateException("메뉴 이미지를 S3에서 삭제하지 못했습니다.", exception);
        }
    }

    @Override
    public URI temporaryReadUrl(String storageKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(MenuImageStorageKey.validate(storageKey))
                .build();
        try {
            return URI.create(presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(READ_URL_LIFETIME)
                    .getObjectRequest(request)
                    .build()).url().toString());
        } catch (SdkException exception) {
            throw new IllegalStateException("메뉴 이미지 접근 URL을 만들지 못했습니다.", exception);
        }
    }

    @Override
    public void close() {
        presigner.close();
        s3Client.close();
    }
}
