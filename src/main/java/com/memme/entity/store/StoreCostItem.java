package com.memme.entity.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_cost_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_cost_store_month", columnNames = {"store_id", "cost_month"}))
public class StoreCostItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "cost_month", nullable = false)
    private LocalDate costMonth;

    @Column(name = "rent_amount", nullable = false)
    private long rentAmount;

    @Column(name = "labor_amount", nullable = false)
    private long laborAmount;

    @Column(name = "ingredient_cost_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal ingredientCostRate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected StoreCostItem() {
    }

    public static StoreCostItem create(Long storeId, LocalDate costMonth, long rentAmount,
            long laborAmount, BigDecimal ingredientCostRate, LocalDateTime now) {
        StoreCostItem item = new StoreCostItem();
        item.storeId = storeId;
        item.costMonth = costMonth;
        item.createdAt = now;
        item.update(rentAmount, laborAmount, ingredientCostRate, now);
        return item;
    }

    public void update(long rentAmount, long laborAmount, BigDecimal ingredientCostRate, LocalDateTime now) {
        this.rentAmount = rentAmount;
        this.laborAmount = laborAmount;
        this.ingredientCostRate = ingredientCostRate;
        this.updatedAt = now;
    }

    public Long getStoreId() { return storeId; }
    public LocalDate getCostMonth() { return costMonth; }
    public long getRentAmount() { return rentAmount; }
    public long getLaborAmount() { return laborAmount; }
    public BigDecimal getIngredientCostRate() { return ingredientCostRate; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
