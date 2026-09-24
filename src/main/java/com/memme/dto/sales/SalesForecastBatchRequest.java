package com.memme.dto.sales;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record SalesForecastBatchRequest(
        @NotNull @Positive Long storeId,
        @NotNull @Positive Long uploadId,
        @NotNull @Positive Long analysisRunId,
        @NotNull LocalDate forecastStartDate,
        @NotEmpty List<@Valid DailySales> dailySales
) {

    public SalesForecastBatchRequest {
        dailySales = dailySales == null ? null : List.copyOf(dailySales);
    }

    public record DailySales(
            @NotNull LocalDate date,
            @NotNull Long amount,
            @NotNull @PositiveOrZero Long orderCnt
    ) {}
}
