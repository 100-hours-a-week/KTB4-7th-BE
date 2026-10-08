package com.memme.service.store;

public interface MenuImageStorage {

    String store(long storeId, byte[] content, String mimeType);

    String signedReadUrl(String storageKey);

    void delete(String storageKey);
}
