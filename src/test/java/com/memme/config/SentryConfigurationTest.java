package com.memme.config;

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentryConfigurationTest {

    @AfterEach
    void tearDown() {
        Sentry.close();
    }

    @Test
    void DSN이_비어있으면_Sentry를_활성화하지_않는다() {
        Sentry.close();

        try (SentryConfiguration.SentryInitializer ignored =
                     new SentryConfiguration.SentryInitializer("", "local")) {
            assertThat(Sentry.isEnabled()).isFalse();
        }
    }

    @Test
    void Sentry_옵션에_DSN과_환경을_설정하고_PII_전송을_비활성화한다() {
        String dsn = "https://public@example.com/1";

        SentryOptions options = SentryConfiguration.SentryInitializer.createOptions(dsn, "production");

        assertThat(options.getDsn()).isEqualTo(dsn);
        assertThat(options.getEnvironment()).isEqualTo("production");
        assertThat(options.isSendDefaultPii()).isFalse();
        assertThat(options.getInAppIncludes()).contains("com.memme");
    }
}
