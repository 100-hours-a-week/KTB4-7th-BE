package com.memme.service.sales;

import java.time.LocalDate;
import java.util.Objects;

public record SalesOrderItem(
    SalesOrderKey orderKey,
    LocalDate orderDate,
    SalesOrderStatus status,
    String menuNameRaw,
    String menuName,
    String menuKey,
    String categoryRaw,
    StandardMenuCategory category,
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
    SalesItemType itemType
) {

    public SalesOrderItem {
        Objects.requireNonNull(orderKey, "orderKey");
        Objects.requireNonNull(orderDate, "orderDate");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(menuNameRaw, "menuNameRaw");
        Objects.requireNonNull(menuName, "menuName");
        Objects.requireNonNull(menuKey, "menuKey");
        Objects.requireNonNull(categoryRaw, "categoryRaw");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(optionRaw, "optionRaw");
        Objects.requireNonNull(itemDiscountName, "itemDiscountName");
        Objects.requireNonNull(orderDiscountName, "orderDiscountName");
        Objects.requireNonNull(itemType, "itemType");
    }
}
