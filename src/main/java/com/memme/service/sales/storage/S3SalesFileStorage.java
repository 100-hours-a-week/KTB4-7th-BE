package com.memme.service.sales.storage;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@ConditionalOnProperty(name = "app.sales.storage.type", havingValue = "s3")
public class S3SalesFileStorage implements SalesFileStorage {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final S3Client s3Client;
    private final String bucket;

    public S3SalesFileStorage(
            S3Client s3Client,
            @Value("${app.sales.storage.s3.bucket}") String bucket
    ) {
        this.s3Client = s3Client;
        this.bucket = requireText(bucket, "bucket");
    }

    @Override
    public StoredSalesFile store(SalesFile file) {
        String storageKey = createStorageKey(file);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(XLSX_CONTENT_TYPE)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(file.content()));
            return new StoredSalesFile(storageKey);
        } catch (SdkException exception) {
            throw new IllegalStateException("매출 파일을 S3에 저장하지 못했습니다.", exception);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        String validStorageKey = requireText(storageKey, "storageKey");
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(validStorageKey)
                .build();
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return response.asByteArray();
        } catch (SdkException exception) {
            throw new IllegalStateException("매출 파일을 S3에서 읽지 못했습니다.", exception);
        }
    }

    private String createStorageKey(SalesFile file) {
        return file.storeId() + "/" + file.checksum().substring(0, 2)
                + "/" + UUID.randomUUID() + "-" + file.checksum() + ".xlsx";
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
