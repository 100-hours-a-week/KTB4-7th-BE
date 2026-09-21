package com.memme.service.sales.storage;

public interface SalesFileStorage {

    StoredSalesFile store(SalesFile file);

    byte[] load(String storageKey);
}
