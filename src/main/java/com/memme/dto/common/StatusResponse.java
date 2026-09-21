package com.memme.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

public record StatusResponse<T>(
        @JsonInclude(JsonInclude.Include.NON_NULL) String message,
        String status,
        T data
) {}
