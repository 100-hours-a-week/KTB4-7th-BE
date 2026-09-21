package com.memme.service.sales.storage;

public record StoredSalesFile(String storageKey) {

    public StoredSalesFile {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey must not be blank");
        }
    }
}
