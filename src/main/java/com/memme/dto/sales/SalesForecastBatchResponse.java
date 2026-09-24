package com.memme.dto.sales;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalesForecastBatchResponse(
        @NotBlank String message,
        SalesForecastStatus status,
        @NotNull @Valid Data data
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            LocalDate forecastStartDate,
            LocalDate forecastEndDate,
            @Positive Integer horizonDays,
            List<@Valid Prediction> predictions,
            List<@NotBlank String> missingData
    ) {

        public Data {
            predictions = predictions == null ? null : List.copyOf(predictions);
            missingData = missingData == null ? null : List.copyOf(missingData);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prediction(
            @NotNull LocalDate targetDate,
            @NotNull @PositiveOrZero Long predictedSalesAmount,
            @NotNull @PositiveOrZero Long lowerBound,
            @NotNull @PositiveOrZero Long upperBound,
            @NotBlank @Size(max = 50) String modelVersion
    ) {}
}
