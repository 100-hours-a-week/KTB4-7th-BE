package com.memme.service.cache;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "TEST_REDIS_ENABLED", matches = "true")
class SharedJsonCacheIntegrationTest {
    @Test
    void twoClientsShareTypedJsonAndSetExpiryWithoutTouchingSessionKeys() {
        var config = new RedisStandaloneConfiguration(System.getenv().getOrDefault("TEST_REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("TEST_REDIS_PORT", "6379")));
        String username = System.getenv().getOrDefault("TEST_REDIS_USERNAME", "");
        String password = System.getenv().getOrDefault("TEST_REDIS_PASSWORD", "");
        if (!username.isBlank()) config.setUsername(username);
        if (!password.isBlank()) config.setPassword(password);
        var factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        String namespace = "memme:prod:v2:cache:test:" + UUID.randomUUID();
        var redis = new StringRedisTemplate(factory);
        try {
            var first = new SharedJsonCache(redis, true, namespace);
            var second = new SharedJsonCache(new StringRedisTemplate(factory), true, namespace);
            var expected = new Value(LocalDate.of(2026, 10, 28), "한글 예보");
            first.put("value", expected, Duration.ofMinutes(5));
            assertThat(second.get("value", Value.class)).contains(expected);
            assertThat(redis.getExpire(namespace + ":value")).isBetween(1L, 300L);
            redis.expire(namespace + ":value", Duration.ZERO);
            assertThat(second.get("value", Value.class)).isEmpty();
        } finally {
            redis.delete(namespace + ":value");
            factory.destroy();
        }
    }

    record Value(LocalDate date, String text) {}
}
