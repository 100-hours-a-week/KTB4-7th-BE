package com.memme.dto.store;

public record StoreCostItemPutData(StoreCostItemResponse costItem, String next) {
    public StoreCostItemPutData(StoreCostItemResponse costItem) {
        this(costItem, "PROFIT_ANALYSIS");
    }
}
