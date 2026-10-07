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
import jakarta.persistence.UniqueConstraint;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "menus",
        indexes = @Index(name = "idx_menus_store_current_order",
                columnList = "store_id,removed_at,sort_order,id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_menus_active_tuple",
                columnNames = {"store_id", "active_normalized_name", "price", "category"}))
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Column(name = "active_normalized_name", length = 100, insertable = false, updatable = false)
    private String activeNormalizedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private MenuCategory category;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MenuStatus status;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Menu() {
    }

    public static Menu create(Long storeId, String name, MenuCategory category,
            long price, int sortOrder, LocalDateTime now) {
        Menu menu = new Menu();
        menu.storeId = Objects.requireNonNull(storeId, "storeId");
        menu.category = Objects.requireNonNull(category, "category");
        menu.createdAt = Objects.requireNonNull(now, "now");
        menu.updatedAt = now;
        menu.status = MenuStatus.ACTIVE;
        menu.name = normalizeName(Objects.requireNonNull(name, "name"));
        menu.normalizedName = menu.name;
        if (menu.normalizedName.isBlank() || menu.normalizedName.length() > 100) {
            throw new IllegalArgumentException("메뉴명은 1~100자여야 합니다.");
        }
        if (price < 0 || price > 1_000_000 || sortOrder < 0) {
            throw new IllegalArgumentException("메뉴 가격 또는 순서가 올바르지 않습니다.");
        }
        menu.price = price;
        menu.sortOrder = sortOrder;
        return menu;
    }

    private static String normalizeName(String name) {
        return Normalizer.normalize(name.trim().replaceAll("\\s+", " "), Normalizer.Form.NFC);
    }

    public void remove(LocalDateTime now) {
        removedAt = Objects.requireNonNull(now, "now");
        updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public String getName() { return name; }
    public String getNormalizedName() { return normalizedName; }
    public MenuCategory getCategory() { return category; }
    public long getPrice() { return price; }
    public int getSortOrder() { return sortOrder; }
    public MenuStatus getStatus() { return status; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
