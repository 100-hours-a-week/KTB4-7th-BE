package com.memme.service.solution.external;

import java.time.LocalDateTime;

public record KmaWeatherAlert(String name, String areaCode, LocalDateTime effectiveAt) {
}
