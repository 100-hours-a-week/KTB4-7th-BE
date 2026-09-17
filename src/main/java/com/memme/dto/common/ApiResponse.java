package com.memme.dto.common;

public record ApiResponse<T>(
        String message,
        T data
) {
}
