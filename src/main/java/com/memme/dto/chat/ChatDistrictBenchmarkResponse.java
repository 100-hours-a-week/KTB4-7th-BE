package com.memme.dto.chat;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record ChatDistrictBenchmarkResponse(
        @NotBlank @Pattern(regexp = "^[1-9]\\d{3}Q[1-4]$") String basisQuarter,
        @NotNull @PositiveOrZero Integer districtAvgMonthlySalesPerCafe,
        @NotNull @Min(3) Integer cafeStoreCount,
        @NotBlank @Pattern(regexp = "^(00-06|06-11|11-14|14-17|17-21|21-24)$") String peakSalesHourBand,
        @NotBlank @Pattern(regexp = "^(00-06|06-11|11-14|14-17|17-21|21-24)$") String peakFootTrafficHourBand
) {}
