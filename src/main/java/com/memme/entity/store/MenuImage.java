package com.memme.entity.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "menu_images", uniqueConstraints = {
        @UniqueConstraint(name = "uk_menu_image_batch_order", columnNames = {"batch_id", "image_order"}),
        @UniqueConstraint(name = "uk_menu_image_storage_key", columnNames = "storage_key")
})
public class MenuImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "image_order", nullable = false)
    private int imageOrder;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "mime_type", nullable = false, length = 30)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_status", nullable = false, length = 20)
    private MenuProcessingStatus ocrStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ocr_result", columnDefinition = "json")
    private String ocrResult;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MenuImage() {
    }

    public static MenuImage pending(Long batchId, Long storeId, int imageOrder,
            String storageKey, long fileSizeBytes, String mimeType, LocalDateTime createdAt) {
        if (imageOrder < 1 || imageOrder > 10 || fileSizeBytes < 0 || fileSizeBytes > 5_242_880) {
            throw new IllegalArgumentException("이미지 순서 또는 크기가 올바르지 않습니다.");
        }
        if (!"image/jpeg".equals(mimeType) && !"image/png".equals(mimeType)) {
            throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다.");
        }
        MenuImage image = new MenuImage();
        image.batchId = Objects.requireNonNull(batchId, "batchId");
        image.storeId = Objects.requireNonNull(storeId, "storeId");
        image.imageOrder = imageOrder;
        image.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        image.fileSizeBytes = fileSizeBytes;
        image.mimeType = mimeType;
        image.ocrStatus = MenuProcessingStatus.PENDING;
        image.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return image;
    }

    public void startProcessing() {
        if (ocrStatus != MenuProcessingStatus.PENDING) {
            throw new IllegalStateException("처리 대기 중인 이미지만 시작할 수 있습니다.");
        }
        ocrStatus = MenuProcessingStatus.PROCESSING;
    }

    public void complete(String ocrResult) {
        if (ocrStatus != MenuProcessingStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 이미지만 완료할 수 있습니다.");
        }
        this.ocrResult = Objects.requireNonNull(ocrResult, "ocrResult");
        ocrStatus = MenuProcessingStatus.COMPLETED;
    }

    public void fail() {
        if (ocrStatus == MenuProcessingStatus.COMPLETED) {
            throw new IllegalStateException("완료된 이미지는 실패 처리할 수 없습니다.");
        }
        ocrStatus = MenuProcessingStatus.FAILED;
    }

    public Long getId() { return id; }
    public Long getBatchId() { return batchId; }
    public Long getStoreId() { return storeId; }
    public int getImageOrder() { return imageOrder; }
    public String getStorageKey() { return storageKey; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getMimeType() { return mimeType; }
    public MenuProcessingStatus getOcrStatus() { return ocrStatus; }
    public String getOcrResult() { return ocrResult; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
