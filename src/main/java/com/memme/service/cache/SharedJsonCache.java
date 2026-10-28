package com.memme.service.cache;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SharedJsonCache {
    private static final Logger log = LoggerFactory.getLogger(SharedJsonCache.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final String namespace;

    public SharedJsonCache(StringRedisTemplate redis,
            @Value("${app.cache.enabled:false}") boolean enabled,
            @Value("${app.cache.namespace:memme:local:cache:v1}") String namespace) {
        if (namespace == null || !namespace.matches("[a-zA-Z0-9:_-]{1,100}")) {
            throw new IllegalArgumentException("업무 캐시 접두어가 올바르지 않습니다.");
        }
        this.redis = redis;
        this.enabled = enabled;
        this.namespace = namespace;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        if (!enabled) return Optional.empty();
        try {
            String value = redis.opsForValue().get(namespace + ":" + key);
            return value == null ? Optional.empty() : Optional.ofNullable(JSON.readValue(value, type));
        } catch (RuntimeException exception) {
            log.warn("Business cache read bypassed: reason={}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        if (!enabled || value == null) return;
        try {
            redis.opsForValue().set(namespace + ":" + key, JSON.writeValueAsString(value), ttl);
        } catch (RuntimeException exception) {
            log.warn("Business cache write bypassed: reason={}", exception.getClass().getSimpleName());
        }
    }
}
