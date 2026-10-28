package com.memme.dto.noti;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FcmTokenRequest(
        @NotBlank @Size(max = 4096) String token
) {
    @Override
    public String toString() {
        return "FcmTokenRequest[token=<redacted>]";
    }
}
