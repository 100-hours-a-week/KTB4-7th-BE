package com.memme.dto.chat;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatWeatherResponse(
        @NotBlank String basisDateTime,
        @NotBlank String skyText,
        @NotNull Integer minTempC,
        @NotNull Integer maxTempC,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double maxPop,
        @NotNull List<@NotNull @Min(0) @Max(23) Integer> rainHours,
        @JsonInclude(JsonInclude.Include.NON_NULL) @DecimalMin("0.0") Double precipitationMm,
        @NotNull Boolean isNotable
) {
    public ChatWeatherResponse {
        rainHours = rainHours == null ? null : List.copyOf(rainHours);
    }
}
