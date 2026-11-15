package com.memme.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.memme.service.ranking.RankingRecalculationLockProperties;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ranking.recalculation", name = "lock-provider", havingValue = "redis")
public class RankingRedissonConfiguration {

    @Bean(destroyMethod = "shutdown")
    RedissonClient rankingRecalculationRedissonClient(
            RankingRecalculationLockProperties lockProperties,
            @Value("${spring.data.redis.url:}") String url,
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.username:}") String username,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean sslEnabled,
            @Value("${spring.data.redis.timeout:2s}") Duration timeout,
            @Value("${spring.data.redis.connect-timeout:2s}") Duration connectTimeout
    ) {
        return Redisson.create(createConfig(
                lockProperties,
                url,
                host,
                port,
                username,
                password,
                database,
                sslEnabled,
                timeout,
                connectTimeout
        ));
    }

    static Config createConfig(
            RankingRecalculationLockProperties lockProperties,
            String url,
            String host,
            int port,
            String username,
            String password,
            int database,
            boolean sslEnabled,
            Duration timeout,
            Duration connectTimeout
    ) {
        Config config = new Config();
        config.setLockWatchdogTimeout(lockProperties.getLockWatchdogTimeout().toMillis());
        config.setCheckLockSyncedSlaves(true);

        RedisEndpoint endpoint = endpoint(url, host, port, username, password, database, sslEnabled);
        SingleServerConfig server = config.useSingleServer()
                .setAddress(endpoint.address())
                .setDatabase(endpoint.database())
                .setTimeout(Math.toIntExact(timeout.toMillis()))
                .setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
        if (!endpoint.username().isBlank()) {
            server.setUsername(endpoint.username());
        }
        if (!endpoint.password().isBlank()) {
            server.setPassword(endpoint.password());
        }
        return config;
    }

    static RedisEndpoint endpoint(
            String url,
            String host,
            int port,
            String username,
            String password,
            int database,
            boolean sslEnabled
    ) {
        if (url == null || url.isBlank()) {
            String scheme = sslEnabled ? "rediss" : "redis";
            return new RedisEndpoint(scheme + "://" + formatHost(host) + ":" + port,
                    blankIfNull(username), blankIfNull(password), database);
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid spring.data.redis.url.");
        }
        String scheme = uri.getScheme();
        if (!"redis".equalsIgnoreCase(scheme) && !"rediss".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("spring.data.redis.url must use redis:// or rediss://.");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("spring.data.redis.url must include a Redis host.");
        }

        int effectivePort = uri.getPort() < 0 ? port : uri.getPort();
        String rawUserInfo = uri.getRawUserInfo();
        String effectiveUsername = blankIfNull(username);
        String effectivePassword = blankIfNull(password);
        if (rawUserInfo != null) {
            int separator = rawUserInfo.indexOf(':');
            if (separator < 0) {
                effectiveUsername = decode(rawUserInfo);
            } else {
                effectiveUsername = decode(rawUserInfo.substring(0, separator));
                effectivePassword = decode(rawUserInfo.substring(separator + 1));
            }
        }

        int effectiveDatabase = database;
        String path = uri.getPath();
        if (path != null && path.length() > 1) {
            try {
                effectiveDatabase = Integer.parseInt(path.substring(1));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("spring.data.redis.url database path must be an integer.");
            }
        }

        return new RedisEndpoint(scheme.toLowerCase() + "://" + formatHost(uri.getHost()) + ":" + effectivePort,
                effectiveUsername, effectivePassword, effectiveDatabase);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid credentials encoding in spring.data.redis.url.");
        }
    }

    private static String formatHost(String host) {
        if (host != null && host.contains(":") && !host.startsWith("[")) {
            return "[" + host + "]";
        }
        return host;
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    record RedisEndpoint(String address, String username, String password, int database) {
    }
}
