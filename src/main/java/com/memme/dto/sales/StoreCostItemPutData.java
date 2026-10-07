package com.memme.dto.sales;

public record StoreCostItemPutData(StoreCostItemResponse costItem, String next) {
    public StoreCostItemPutData(StoreCostItemResponse costItem) {
        this(costItem, "PROFIT_ANALYSIS");
    }
}
