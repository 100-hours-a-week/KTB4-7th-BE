package com.memme.service.sales.upload;

import java.util.Objects;

public record SalesUploadCommand(
        Long storeId,
        Long requestedByUserId,
        String originalFileName,
        byte[] content
) {

    public SalesUploadCommand {
        Objects.requireNonNull(storeId, "storeId");
        Objects.requireNonNull(requestedByUserId, "requestedByUserId");
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("originalFileName must not be blank");
        }
        content = Objects.requireNonNull(content, "content").clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
