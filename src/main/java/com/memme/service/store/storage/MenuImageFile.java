package com.memme.service.store.storage;

import java.util.Objects;

public record MenuImageFile(long storeId, String mimeType, byte[] content) {

    public MenuImageFile {
        if (storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive");
        }
        if (!"image/jpeg".equals(mimeType) && !"image/png".equals(mimeType)) {
            throw new IllegalArgumentException("unsupported image mimeType");
        }
        content = Objects.requireNonNull(content, "content").clone();
        if (content.length == 0 || content.length > 5_242_880) {
            throw new IllegalArgumentException("image size must be between 1 byte and 5 MiB");
        }
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public String extension() {
        return "image/png".equals(mimeType) ? "png" : "jpg";
    }
}
