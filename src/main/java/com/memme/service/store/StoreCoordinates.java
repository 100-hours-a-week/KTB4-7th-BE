package com.memme.service.store;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record StoreCoordinates(BigDecimal latitude, BigDecimal longitude) {

    public StoreCoordinates {
        Objects.requireNonNull(latitude, "latitude");
        Objects.requireNonNull(longitude, "longitude");
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException("위도 범위를 벗어났습니다.");
        }
        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("경도 범위를 벗어났습니다.");
        }
        latitude = latitude.setScale(7, RoundingMode.HALF_UP);
        longitude = longitude.setScale(7, RoundingMode.HALF_UP);
    }
}
