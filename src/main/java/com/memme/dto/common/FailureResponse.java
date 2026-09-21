package com.memme.dto.common;

public record FailureResponse(
        String message,
        String failReason,
        Object data
) {}
