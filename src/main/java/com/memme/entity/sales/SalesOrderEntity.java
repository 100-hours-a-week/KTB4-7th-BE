package com.memme.entity.sales;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sales_orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sales_orders_natural_key",
                columnNames = {"store_id", "channel", "pos_order_no", "ordered_at"}
        )
)
public class SalesOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_upload_id", nullable = false)
    private Long salesUploadId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private SalesOrderChannel channel;

    @Column(name = "pos_order_no", nullable = false, length = 100)
    private String posOrderNo;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "is_valid", nullable = false)
    private boolean valid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SalesOrderEntity() {
    }

    private SalesOrderEntity(
            Long salesUploadId,
            Long storeId,
            SalesOrderChannel channel,
            String posOrderNo,
            LocalDateTime orderedAt,
            boolean valid
    ) {
        this.salesUploadId = Objects.requireNonNull(salesUploadId, "salesUploadId");
        this.storeId = Objects.requireNonNull(storeId, "storeId");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.posOrderNo = requireText(posOrderNo, "posOrderNo");
        this.orderedAt = Objects.requireNonNull(orderedAt, "orderedAt");
        this.valid = valid;
    }

    public static SalesOrderEntity create(
            Long salesUploadId,
            Long storeId,
            SalesOrderChannel channel,
            String posOrderNo,
            LocalDateTime orderedAt,
            boolean valid
    ) {
        return new SalesOrderEntity(salesUploadId, storeId, channel, posOrderNo, orderedAt, valid);
    }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getSalesUploadId() {
        return salesUploadId;
    }

    public Long getStoreId() {
        return storeId;
    }

    public SalesOrderChannel getChannel() {
        return channel;
    }

    public String getPosOrderNo() {
        return posOrderNo;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }

    public boolean isValid() {
        return valid;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
