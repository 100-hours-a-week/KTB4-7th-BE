package com.memme.dto.sales;

import java.math.BigDecimal;
import java.util.List;

public record SalesHourlyProfilesResponse(
        List<HourlyProfile> hourlyProfiles
) {

    public record HourlyProfile(int hour, BigDecimal netSales, long orderCount) {}
}
