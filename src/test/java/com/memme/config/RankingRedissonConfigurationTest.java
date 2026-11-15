package com.memme.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import com.memme.service.ranking.RankingRecalculationLockProperties;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;

class RankingRedissonConfigurationTest {

    @Test
    void readsAddressCredentialsAndDatabaseFromTheStandardSpringRedisUrl() {
        RankingRecalculationLockProperties properties = new RankingRecalculationLockProperties();
        properties.setLockWatchdogTimeout(Duration.ofSeconds(45));

        RankingRedissonConfiguration.RedisEndpoint endpoint = RankingRedissonConfiguration.endpoint(
                "rediss://lock-user:secret%2Bvalue@redis.example:6380/4",
                "ignored-host",
                6379,
                "ignored-user",
                "ignored-password",
                0,
                false
        );
        Config config = RankingRedissonConfiguration.createConfig(properties,
                "rediss://lock-user:secret%2Bvalue@redis.example:6380/4", "ignored-host", 6379,
                "ignored-user", "ignored-password", 0, false, Duration.ofMillis(800), Duration.ofMillis(900));

        assertThat(config.getLockWatchdogTimeout()).isEqualTo(45_000L);
        assertThat(config.isCheckLockSyncedSlaves()).isTrue();
        assertThat(endpoint.address()).isEqualTo("rediss://redis.example:6380");
        assertThat(endpoint.username()).isEqualTo("lock-user");
        assertThat(endpoint.password()).isEqualTo("secret+value");
        assertThat(endpoint.database()).isEqualTo(4);
    }

    @Test
    void buildsTlsAddressFromStandardHostPropertiesWhenUrlIsAbsent() {
        RankingRedissonConfiguration.RedisEndpoint endpoint = RankingRedissonConfiguration.endpoint(
                "",
                "redis.internal",
                6380,
                "app-user",
                "app-password",
                2,
                true
        );

        assertThat(endpoint.address()).isEqualTo("rediss://redis.internal:6380");
        assertThat(endpoint.username()).isEqualTo("app-user");
        assertThat(endpoint.password()).isEqualTo("app-password");
        assertThat(endpoint.database()).isEqualTo(2);
    }

    @Test
    void rejectsUnsupportedRedisUrlSchemesWithoutEchoingCredentials() {
        assertThatThrownBy(() -> RankingRedissonConfiguration.endpoint(
                "http://user:secret@redis.example:6379/0",
                "localhost",
                6379,
                "",
                "",
                0,
                false
        )).hasMessage("spring.data.redis.url must use redis:// or rediss://.")
                .hasMessageNotContaining("secret");
    }
}
