package com.memme.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

@Configuration(proxyBeanMethods = false)
@Profile("production-redis")
@EnableSpringHttpSession
public class ProductionRedisSessionConfig {
    static final String NAMESPACE = "memme:prod:v2";
    static final String PREFIX = NAMESPACE + ":sessions:";

    @Bean
    RedisSessionRepository sessionRepository(RedisConnectionFactory factory) {
        var template = new RedisTemplate<String, Object>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new HashedSessionKeySerializer());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(new JdkSerializationRedisSerializer());
        template.setValueSerializer(new JdkSerializationRedisSerializer());
        template.afterPropertiesSet();
        var repository = new RedisSessionRepository(template);
        repository.setRedisKeyNamespace(NAMESPACE);
        repository.setDefaultMaxInactiveInterval(java.time.Duration.ofMinutes(30));
        return repository;
    }

    @Bean
    DefaultCookieSerializer cookieSerializer() {
        var cookie = new DefaultCookieSerializer();
        cookie.setCookieName("JSESSIONID");
        cookie.setCookiePath("/");
        cookie.setUseHttpOnlyCookie(true);
        cookie.setUseSecureCookie(true);
        cookie.setSameSite("Lax");
        return cookie;
    }

    static class HashedSessionKeySerializer implements RedisSerializer<String> {
        public byte[] serialize(String value) {
            if (value == null) return null;
            if (!value.startsWith(PREFIX)) throw new IllegalArgumentException("Unexpected session namespace");
            try {
                var bytes = MessageDigest.getInstance("SHA-256").digest(value.substring(PREFIX.length()).getBytes(StandardCharsets.UTF_8));
                return (PREFIX + HexFormat.of().formatHex(bytes)).getBytes(StandardCharsets.UTF_8);
            } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        }
        public String deserialize(byte[] value) { return value == null ? null : new String(value, StandardCharsets.UTF_8); }
    }
}
