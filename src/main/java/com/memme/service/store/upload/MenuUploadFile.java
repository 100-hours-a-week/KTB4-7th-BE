package com.memme.service.store.upload;

import java.util.Objects;

public record MenuUploadFile(String fileName, String mimeType, byte[] content) {

    public MenuUploadFile {
        content = Objects.requireNonNull(content, "content").clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
