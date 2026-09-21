package com.memme.dto.common;

public record ErrorResponse(
        String message,
        Object data
) {}
