package com.memme.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClockConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfig.class)
            .withPropertyValues("spring.profiles.active=local");

    @Test
    void local_환경에서_고정_시간이_설정되면_해당_시간을_사용한다() {
        contextRunner
                .withPropertyValues("APP_FIXED_NOW=2026-10-01T00:01:00+09:00")
                .run(context -> {
                    Clock clock = context.getBean(Clock.class);

                    assertThat(clock.instant())
                            .isEqualTo(Instant.parse("2026-09-30T15:01:00Z"));
                    assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
                });
    }
}
