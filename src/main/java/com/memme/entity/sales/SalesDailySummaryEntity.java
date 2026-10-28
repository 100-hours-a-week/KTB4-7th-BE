package com.memme.entity.sales;

import java.time.LocalDate;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sales_daily_summaries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sales_daily_summaries_store_date",
                columnNames = {"store_id", "sales_date"}
        )
)
public class SalesDailySummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "sales_date", nullable = false)
    private LocalDate salesDate;

    @Column(name = "total_net_amount", nullable = false)
    private long totalNetAmount;

    @Column(name = "menu_net_amount", nullable = false)
    private long menuNetAmount;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Column(name = "sales_activity_count", nullable = false)
    private int salesActivityCount;

    @Column(name = "menu_quantity", nullable = false)
    private int menuQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_status", nullable = false, length = 20)
    private SalesDailyStatus dayStatus;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SalesDailySummaryEntity() {
    }

    private SalesDailySummaryEntity(
            Long storeId,
            LocalDate salesDate,
            long totalNetAmount,
            long menuNetAmount,
            int orderCount,
            int menuQuantity,
            SalesDailyStatus dayStatus
    ) {
        this.storeId = Objects.requireNonNull(storeId, "storeId");
        this.salesDate = Objects.requireNonNull(salesDate, "salesDate");
        update(totalNetAmount, menuNetAmount, orderCount, menuQuantity, 0, dayStatus);
    }

    public static SalesDailySummaryEntity create(
            Long storeId,
            LocalDate salesDate,
            long totalNetAmount,
            long menuNetAmount,
            int orderCount,
            int menuQuantity
    ) {
        return create(storeId, salesDate, totalNetAmount, menuNetAmount, orderCount, menuQuantity,
                SalesDailyStatus.UNKNOWN);
    }

    public static SalesDailySummaryEntity create(
            Long storeId,
            LocalDate salesDate,
            long totalNetAmount,
            long menuNetAmount,
            int orderCount,
            int menuQuantity,
            SalesDailyStatus dayStatus
    ) {
        return new SalesDailySummaryEntity(
                storeId,
                salesDate,
                totalNetAmount,
                menuNetAmount,
                orderCount,
                menuQuantity,
                dayStatus
        );
    }

    public static SalesDailySummaryEntity create(
            Long storeId,
            LocalDate salesDate,
            long totalNetAmount,
            long menuNetAmount,
            int orderCount,
            int menuQuantity,
            int salesActivityCount,
            SalesDailyStatus dayStatus
    ) {
        SalesDailySummaryEntity summary = new SalesDailySummaryEntity(
                storeId,
                salesDate,
                totalNetAmount,
                menuNetAmount,
                orderCount,
                menuQuantity,
                dayStatus
        );
        summary.setSalesActivityCount(salesActivityCount);
        return summary;
    }

    public void update(
            long totalNetAmount,
            long menuNetAmount,
            int orderCount,
            int menuQuantity
    ) {
        update(totalNetAmount, menuNetAmount, orderCount, menuQuantity, 0, dayStatus);
    }

    public void update(
            long totalNetAmount,
            long menuNetAmount,
            int orderCount,
            int menuQuantity,
            SalesDailyStatus dayStatus
    ) {
        update(totalNetAmount, menuNetAmount, orderCount, menuQuantity, 0, dayStatus);
    }

    public void update(
            long totalNetAmount,
            long menuNetAmount,
            int orderCount,
            int menuQuantity,
            int salesActivityCount,
            SalesDailyStatus dayStatus
    ) {
        if (orderCount < 0) {
            throw new IllegalArgumentException("orderCount must not be negative");
        }
        setSalesActivityCount(salesActivityCount);
        this.dayStatus = Objects.requireNonNull(dayStatus, "dayStatus");
        this.totalNetAmount = totalNetAmount;
        this.menuNetAmount = menuNetAmount;
        this.orderCount = orderCount;
        this.menuQuantity = menuQuantity;
    }

    private void setSalesActivityCount(int salesActivityCount) {
        if (salesActivityCount < 0) {
            throw new IllegalArgumentException("salesActivityCount must not be negative");
        }
        this.salesActivityCount = salesActivityCount;
    }

    @PrePersist
    @PreUpdate
    private void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public LocalDate getSalesDate() {
        return salesDate;
    }

    public long getTotalNetAmount() {
        return totalNetAmount;
    }

    public long getMenuNetAmount() {
        return menuNetAmount;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public int getSalesActivityCount() {
        return salesActivityCount;
    }

    public int getMenuQuantity() {
        return menuQuantity;
    }

    public SalesDailyStatus getDayStatus() {
        return dayStatus;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
