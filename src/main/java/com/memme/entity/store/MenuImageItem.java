package com.memme.entity.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "menu_image_items", indexes = {
        @Index(name = "idx_menu_image_items_image_status", columnList = "menu_image_id,status"),
        @Index(name = "idx_menu_image_items_menu", columnList = "menu_id")
})
public class MenuImageItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_image_id", nullable = false)
    private Long menuImageId;

    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "detected_name", nullable = false, length = 100)
    private String detectedName;

    @Column(name = "detected_category", length = 50)
    private String detectedCategory;

    @Column(name = "detected_price")
    private Long detectedPrice;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "price_review_required", nullable = false)
    private boolean priceReviewRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MenuImageItemStatus status;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MenuImageItem() {
    }

    public static MenuImageItem pending(Long menuImageId, String detectedName,
            String detectedCategory, Long detectedPrice, int sortOrder,
            BigDecimal confidence, boolean priceReviewRequired, LocalDateTime createdAt) {
        if (detectedPrice != null && (detectedPrice < 0 || detectedPrice > 1_000_000)) {
            throw new IllegalArgumentException("인식 가격이 범위를 벗어났습니다.");
        }
        if (sortOrder < 0 || (confidence != null && (confidence.signum() < 0
                || confidence.compareTo(BigDecimal.ONE) > 0))) {
            throw new IllegalArgumentException("인식 순서 또는 신뢰도가 올바르지 않습니다.");
        }
        MenuImageItem item = new MenuImageItem();
        item.menuImageId = Objects.requireNonNull(menuImageId, "menuImageId");
        item.detectedName = Objects.requireNonNull(detectedName, "detectedName");
        item.detectedCategory = detectedCategory;
        item.detectedPrice = detectedPrice;
        item.sortOrder = sortOrder;
        item.confidence = confidence;
        item.priceReviewRequired = priceReviewRequired;
        item.status = MenuImageItemStatus.PENDING;
        item.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        return item;
    }

    public void confirm(Long menuId, LocalDateTime reviewedAt) {
        if (status != MenuImageItemStatus.PENDING) {
            throw new IllegalStateException("검토 대기 중인 후보만 확정할 수 있습니다.");
        }
        this.menuId = Objects.requireNonNull(menuId, "menuId");
        this.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt");
        status = MenuImageItemStatus.CONFIRMED;
    }

    public void reject(LocalDateTime reviewedAt) {
        if (status != MenuImageItemStatus.PENDING) {
            throw new IllegalStateException("검토 대기 중인 후보만 제외할 수 있습니다.");
        }
        this.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt");
        status = MenuImageItemStatus.REJECTED;
    }

    public Long getId() { return id; }
    public Long getMenuImageId() { return menuImageId; }
    public Long getMenuId() { return menuId; }
    public String getDetectedName() { return detectedName; }
    public String getDetectedCategory() { return detectedCategory; }
    public Long getDetectedPrice() { return detectedPrice; }
    public int getSortOrder() { return sortOrder; }
    public BigDecimal getConfidence() { return confidence; }
    public boolean isPriceReviewRequired() { return priceReviewRequired; }
    public MenuImageItemStatus getStatus() { return status; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
