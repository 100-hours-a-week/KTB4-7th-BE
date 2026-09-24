package com.memme.dto.store;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record StoreProfileResponse(
        Store store
) {

    public record Store(
            Long id,
            String businessRegNumber,
            String storeName,
            Address address,
            List<BusinessHours> businessHours
    ) {
    }

    public record Address(
            String postalCode,
            String roadAddress,
            String addressDetail
    ) {
    }

    public record BusinessHours(
            DayOfWeek dayOfWeek,
            Boolean isClosed,
            LocalTime openTime,
            LocalTime closeTime
    ) {
    }
}
