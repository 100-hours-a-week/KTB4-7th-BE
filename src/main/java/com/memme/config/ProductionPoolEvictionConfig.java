package com.memme.config;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@Profile("production-pool-evict")
public class ProductionPoolEvictionConfig {
    @Bean(destroyMethod = "close")
    PoolControl productionPoolControl(DataSource source, RedisConnectionFactory factory,
            @Value("${production.pool.experiment}") String experiment) {
        if (!"production-db-cutover-v2".equals(experiment) || !(source instanceof HikariDataSource hikari)) {
            throw new IllegalStateException("Production pool control configuration mismatch");
        }
        return new PoolControl(hikari, new StringRedisTemplate(factory), experiment, Clock.systemUTC());
    }

    static final class PoolControl implements AutoCloseable {
        static final String PREFIX = "memme:prod:v2:pool-control:";
        private static final Logger LOG = LoggerFactory.getLogger(PoolControl.class);
        private final HikariDataSource pool;
        private final StringRedisTemplate redis;
        private final String experiment;
        private final Clock clock;
        private final String boot = UUID.randomUUID().toString();
        private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("production-pool-control").factory());
        private String handled = "";
        private long heartbeat;
        private long lastWarning;

        PoolControl(HikariDataSource pool, StringRedisTemplate redis, String experiment, Clock clock) {
            this.pool = pool;
            this.redis = redis;
            this.experiment = experiment;
            this.clock = clock;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void start() {
            LOG.info("PRODUCTION_POOL_CONTROL_READY boot={}", boot);
            worker.scheduleWithFixedDelay(this::poll, 0, 100, TimeUnit.MILLISECONDS);
        }

        void poll() {
            long now = clock.millis();
            try {
                if (now - heartbeat >= 2000) {
                    redis.opsForHash().put(PREFIX + experiment + ":registry", boot, Long.toString(now));
                    heartbeat = now;
                }
                var command = redis.opsForHash().entries(PREFIX + experiment + ":command");
                handle(command, clock.millis());
            } catch (RuntimeException failure) {
                if (now - lastWarning >= 30000) {
                    LOG.warn("PRODUCTION_POOL_CONTROL_POLL_FAILED type={}", failure.getClass().getSimpleName());
                    lastWarning = now;
                }
            }
        }

        void handle(Map<Object, Object> command, long now) {
            String nonce = String.valueOf(command.getOrDefault("nonce", ""));
            if (!"soft-evict".equals(command.get("action")) || !experiment.equals(command.get("experiment"))
                    || !nonce.matches("[a-zA-Z0-9-]{1,64}") || nonce.equals(handled)) return;
            long deadline;
            try { deadline = Long.parseLong(String.valueOf(command.get("deadline"))); }
            catch (NumberFormatException invalid) { return; }
            if (deadline <= now || deadline - now > 10000) return;
            var management = pool.getHikariPoolMXBean();
            if (management == null) return;
            int active = management.getActiveConnections();
            int total = management.getTotalConnections();
            now = clock.millis();
            if (deadline <= now || deadline - now > 10000) return;
            management.softEvictConnections();
            handled = nonce;
            redis.opsForHash().put(PREFIX + experiment + ":ack:" + nonce, boot,
                    now + "," + active + "," + total);
            LOG.info("PRODUCTION_POOL_EVICT_ACK boot={} nonce={} at={} active_before={} total_before={}",
                    boot, nonce, now, active, total);
        }

        public void close() { worker.shutdownNow(); }
    }
}
