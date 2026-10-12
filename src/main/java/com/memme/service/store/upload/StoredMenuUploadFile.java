package com.memme.service.store.upload;

public record StoredMenuUploadFile(int order, String storageKey, long fileSizeBytes, String mimeType) {
}
