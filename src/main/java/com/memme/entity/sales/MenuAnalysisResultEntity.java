package com.memme.entity.sales;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "menu_analysis_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_menu_analysis_menu",
                columnNames = {"sales_analysis_id", "menu_id"}
        )
)
public class MenuAnalysisResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_analysis_id", nullable = false)
    private Long salesAnalysisId;

    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "menu_name_snapshot", nullable = false, length = 100)
    private String menuNameSnapshot;

    @Column(name = "sales_amount", nullable = false)
    private long salesAmount;

    @Column(name = "sales_quantity", nullable = false)
    private int salesQuantity;

    @Column(name = "sales_rank")
    private Integer salesRank;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MenuAnalysisResultEntity() {
    }

    private MenuAnalysisResultEntity(
            Long salesAnalysisId,
            Long menuId,
            String menuNameSnapshot,
            long salesAmount,
            int salesQuantity,
            Integer salesRank
    ) {
        this.salesAnalysisId = Objects.requireNonNull(salesAnalysisId, "salesAnalysisId");
        this.menuId = menuId;
        this.menuNameSnapshot = requireText(menuNameSnapshot, "menuNameSnapshot");
        if (salesAmount < 0) {
            throw new IllegalArgumentException("salesAmount must not be negative");
        }
        if (salesQuantity < 0) {
            throw new IllegalArgumentException("salesQuantity must not be negative");
        }
        if (salesRank != null && salesRank <= 0) {
            throw new IllegalArgumentException("salesRank must be positive");
        }
        this.salesAmount = salesAmount;
        this.salesQuantity = salesQuantity;
        this.salesRank = salesRank;
    }

    public static MenuAnalysisResultEntity create(
            Long salesAnalysisId,
            Long menuId,
            String menuNameSnapshot,
            long salesAmount,
            int salesQuantity,
            Integer salesRank
    ) {
        return new MenuAnalysisResultEntity(
                salesAnalysisId,
                menuId,
                menuNameSnapshot,
                salesAmount,
                salesQuantity,
                salesRank
        );
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

    public Long getSalesAnalysisId() {
        return salesAnalysisId;
    }

    public Long getMenuId() {
        return menuId;
    }

    public String getMenuNameSnapshot() {
        return menuNameSnapshot;
    }

    public long getSalesAmount() {
        return salesAmount;
    }

    public int getSalesQuantity() {
        return salesQuantity;
    }

    public Integer getSalesRank() {
        return salesRank;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
