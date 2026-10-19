package com.memme.dto.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ChatLookupResponse<T>(
        @NotNull Status status,
        @JsonInclude(JsonInclude.Include.ALWAYS) @Valid T data
) {
    public ChatLookupResponse {
        if (status == null || (status == Status.READY) != (data != null)) {
            throw new IllegalArgumentException("Lookup status and data must match");
        }
    }

    public static <T> ChatLookupResponse<T> ready(T data) {
        return new ChatLookupResponse<>(Status.READY, data);
    }

    public static <T> ChatLookupResponse<T> empty() {
        return new ChatLookupResponse<>(Status.EMPTY, null);
    }

    public enum Status {
        READY,
        EMPTY
    }
}
