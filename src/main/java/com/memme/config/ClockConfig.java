package com.memme.config;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    @Profile("local")
    public Clock localClock(@Value("${APP_FIXED_NOW:}") String fixedNow) {
        if (fixedNow.isBlank()) {
            return Clock.system(KOREA_ZONE);
        }
        return Clock.fixed(OffsetDateTime.parse(fixedNow).toInstant(), KOREA_ZONE);
    }

    @Bean
    @Profile("!local")
    public Clock clock() {
        return Clock.system(KOREA_ZONE);
    }
}
