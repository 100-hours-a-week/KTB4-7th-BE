package com.memme.dto.sales;

import java.math.BigDecimal;
import java.util.List;

public record InternalSalesHourlyProfilesResponse(
        List<HourlyProfile> hourlyProfiles
) {
    public record HourlyProfile(int hour, BigDecimal menuSales, long orderCount) {
    }
}
