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

@Entity
@Table(name = "sales_order_items")
public class SalesOrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_order_id", nullable = false)
    private Long salesOrderId;

    @Column(name = "menu_id")
    private Long menuId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    private SalesOrderItemStatus orderStatus;

    @Column(name = "menu_name_raw", nullable = false, length = 255)
    private String menuNameRaw;

    @Column(name = "menu_name", nullable = false, length = 100)
    private String menuName;

    @Column(name = "menu_key", nullable = false, length = 191)
    private String menuKey;

    @Column(name = "category_raw", length = 100)
    private String categoryRaw;

    @Enumerated(EnumType.STRING)
    @Column(name = "standard_category", nullable = false, length = 30)
    private SalesStandardMenuCategory standardCategory;

    @Column(name = "option_raw", length = 500)
    private String optionRaw;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "line_price", nullable = false)
    private long linePrice;

    @Column(name = "unit_price", nullable = false)
    private long unitPrice;

    @Column(name = "option_price", nullable = false)
    private long optionPrice;

    @Column(name = "item_discount_name", length = 100)
    private String itemDiscountName;

    @Column(name = "item_discount_amount", nullable = false)
    private long itemDiscountAmount;

    @Column(name = "order_discount_name", length = 100)
    private String orderDiscountName;

    @Column(name = "order_discount_amount", nullable = false)
    private long orderDiscountAmount;

    @Column(name = "net_amount", nullable = false)
    private long netAmount;

    @Column(name = "taxable", nullable = false)
    private boolean taxable;

    @Column(name = "vat_amount", nullable = false)
    private long vatAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private SalesOrderItemType itemType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SalesOrderItemEntity() {
    }

    private SalesOrderItemEntity(Values values) {
        this.salesOrderId = Objects.requireNonNull(values.salesOrderId(), "salesOrderId");
        this.menuId = values.menuId();
        this.orderStatus = Objects.requireNonNull(values.orderStatus(), "orderStatus");
        this.menuNameRaw = requireText(values.menuNameRaw(), "menuNameRaw");
        this.menuName = requireText(values.menuName(), "menuName");
        this.menuKey = requireText(values.menuKey(), "menuKey");
        this.categoryRaw = blankToNull(values.categoryRaw());
        this.standardCategory = Objects.requireNonNull(values.standardCategory(), "standardCategory");
        this.optionRaw = blankToNull(values.optionRaw());
        this.quantity = values.quantity();
        this.linePrice = values.linePrice();
        this.unitPrice = values.unitPrice();
        this.optionPrice = values.optionPrice();
        this.itemDiscountName = blankToNull(values.itemDiscountName());
        this.itemDiscountAmount = values.itemDiscountAmount();
        this.orderDiscountName = blankToNull(values.orderDiscountName());
        this.orderDiscountAmount = values.orderDiscountAmount();
        this.netAmount = values.netAmount();
        this.taxable = values.taxable();
        this.vatAmount = values.vatAmount();
        this.itemType = Objects.requireNonNull(values.itemType(), "itemType");
        validateAmounts();
    }

    public static SalesOrderItemEntity create(Values values) {
        return new SalesOrderItemEntity(Objects.requireNonNull(values, "values"));
    }

    private void validateAmounts() {
        if (quantity == 0) {
            throw new IllegalArgumentException("quantity must not be zero");
        }
        if (orderStatus == SalesOrderItemStatus.CANCELED && quantity >= 0) {
            throw new IllegalArgumentException("canceled item quantity must be negative");
        }
        long expectedLinePrice = Math.multiplyExact(quantity, unitPrice);
        if (linePrice != expectedLinePrice) {
            throw new IllegalArgumentException("linePrice must equal quantity multiplied by unitPrice");
        }
        long expectedNetAmount = Math.addExact(
                Math.addExact(linePrice, optionPrice),
                Math.addExact(itemDiscountAmount, orderDiscountAmount)
        );
        if (netAmount != expectedNetAmount) {
            throw new IllegalArgumentException("netAmount does not match its amount components");
        }
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record Values(
            Long salesOrderId,
            Long menuId,
            SalesOrderItemStatus orderStatus,
            String menuNameRaw,
            String menuName,
            String menuKey,
            String categoryRaw,
            SalesStandardMenuCategory standardCategory,
            String optionRaw,
            int quantity,
            long linePrice,
            long unitPrice,
            long optionPrice,
            String itemDiscountName,
            long itemDiscountAmount,
            String orderDiscountName,
            long orderDiscountAmount,
            long netAmount,
            boolean taxable,
            long vatAmount,
            SalesOrderItemType itemType
    ) {
    }

    public Long getId() {
        return id;
    }

    public Long getSalesOrderId() {
        return salesOrderId;
    }

    public Long getMenuId() {
        return menuId;
    }

    public SalesOrderItemStatus getOrderStatus() {
        return orderStatus;
    }

    public String getMenuNameRaw() {
        return menuNameRaw;
    }

    public String getMenuName() {
        return menuName;
    }

    public String getMenuKey() {
        return menuKey;
    }

    public String getCategoryRaw() {
        return categoryRaw;
    }

    public SalesStandardMenuCategory getStandardCategory() {
        return standardCategory;
    }

    public String getOptionRaw() {
        return optionRaw;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getLinePrice() {
        return linePrice;
    }

    public long getUnitPrice() {
        return unitPrice;
    }

    public long getOptionPrice() {
        return optionPrice;
    }

    public String getItemDiscountName() {
        return itemDiscountName;
    }

    public long getItemDiscountAmount() {
        return itemDiscountAmount;
    }

    public String getOrderDiscountName() {
        return orderDiscountName;
    }

    public long getOrderDiscountAmount() {
        return orderDiscountAmount;
    }

    public long getNetAmount() {
        return netAmount;
    }

    public boolean isTaxable() {
        return taxable;
    }

    public long getVatAmount() {
        return vatAmount;
    }

    public SalesOrderItemType getItemType() {
        return itemType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
