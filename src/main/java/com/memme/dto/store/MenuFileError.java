package com.memme.dto.store;

public record MenuFileError(
        int fileIndex,
        String code,
        String message
) {
}
