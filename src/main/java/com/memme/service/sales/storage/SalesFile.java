package com.memme.service.sales.storage;

import java.util.Objects;

public record SalesFile(
        Long storeId,
        String originalFileName,
        String checksum,
        byte[] content
) {

    public SalesFile {
        Objects.requireNonNull(storeId, "storeId");
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("originalFileName must not be blank");
        }
        if (checksum == null || !checksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("checksum must be a lowercase SHA-256 value");
        }
        content = Objects.requireNonNull(content, "content").clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
