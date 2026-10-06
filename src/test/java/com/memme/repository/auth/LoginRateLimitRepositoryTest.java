package com.memme.repository.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.service.auth.LoginRateLimitPolicy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class LoginRateLimitRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 12, 0);
    private static final Map<String, ReentrantLock> NAMED_LOCKS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Map<String, ReentrantLock>> HELD_LOCKS =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    private JdbcTemplate jdbc;
    private LoginRateLimitRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:login_rate_limit_" + java.util.UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        dataSource.setUsername("sa");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE login_attempts (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  email_hash CHAR(64),
                  ip_hash CHAR(64) NOT NULL,
                  failed_at TIMESTAMP(6) NOT NULL,
                  account_blocked_until TIMESTAMP(6)
                )
                """);
        jdbc.execute("CREATE ALIAS GET_LOCK FOR 'com.memme.repository.auth.LoginRateLimitRepositoryTest.getLock'");
        jdbc.execute("CREATE ALIAS RELEASE_LOCK FOR 'com.memme.repository.auth.LoginRateLimitRepositoryTest.releaseLock'");
        repository = new LoginRateLimitRepository(dataSource, jdbc, new LoginRateLimitPolicy());
    }

    @Test
    void 다섯번째_계정_실패가_쿨다운을_설정하고_차단_요청은_이력을_늘리지_않는다() {
        String emailHash = hash(1);
        String ipHash = hash(2);
        for (int index = 1; index <= 4; index++) {
            insert(emailHash, ipHash, NOW.minusSeconds(index), null);
        }

        assertThat(repository.recordFailureIfAllowed(emailHash, ipHash, NOW)).isEmpty();
        LocalDateTime blockedUntil = NOW.plusSeconds(30);

        assertThat(repository.checkAllowed(emailHash, ipHash, NOW.plusSeconds(1))).contains(blockedUntil);
        assertThat(repository.recordFailureIfAllowed(emailHash, ipHash, NOW.plusSeconds(2))).contains(blockedUntil);
        assertThat(countByEmail(emailHash)).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT MAX(account_blocked_until) FROM login_attempts WHERE email_hash = ?",
                LocalDateTime.class,
                emailHash
        )).isEqualTo(blockedUntil);
    }

    @Test
    void 성공_로그인은_계정_이력만_초기화하고_IP_이력은_유지한다() {
        String emailHash = hash(3);
        String ipHash = hash(4);
        insert(emailHash, ipHash, NOW.minusSeconds(1), null);

        assertThat(repository.clearAccountFailuresIfAllowed(emailHash, ipHash, NOW)).isEmpty();

        assertThat(countByEmail(emailHash)).isZero();
        assertThat(countByIp(ipHash)).isEqualTo(1);
    }

    @Test
    void 여러_연결의_동시_요청도_IP_한도를_넘겨_기록하지_않는다() throws Exception {
        String ipHash = hash(5);
        for (int index = 1; index <= 29; index++) {
            insert(hash(100 + index), ipHash, NOW.minusSeconds(index), null);
        }

        int concurrentRequests = 4;
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        try {
            List<Future<Optional<LocalDateTime>>> results = new ArrayList<>();
            for (int index = 0; index < concurrentRequests; index++) {
                String emailHash = hash(1000 + index);
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 요청 시작 신호를 받지 못했습니다.");
                    }
                    return repository.recordFailureIfAllowed(emailHash, ipHash, NOW);
                }));
            }
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long allowed = 0;
            long limited = 0;
            for (Future<Optional<LocalDateTime>> result : results) {
                if (result.get(5, TimeUnit.SECONDS).isEmpty()) {
                    allowed++;
                } else {
                    limited++;
                }
            }

            assertThat(allowed).isEqualTo(1);
            assertThat(limited).isEqualTo(3);
            assertThat(countByIp(ipHash)).isEqualTo(30);
        } finally {
            executor.shutdownNow();
        }
    }

    public static Integer getLock(Connection connection, String name, Integer timeoutSeconds) throws SQLException {
        ReentrantLock lock = NAMED_LOCKS.computeIfAbsent(name, ignored -> new ReentrantLock());
        try {
            if (!lock.tryLock(timeoutSeconds, TimeUnit.SECONDS)) {
                return 0;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return 0;
        }
        HELD_LOCKS.get().put(name, lock);
        return 1;
    }

    public static Integer releaseLock(Connection connection, String name) {
        ReentrantLock lock = HELD_LOCKS.get().remove(name);
        if (lock == null) {
            return null;
        }
        lock.unlock();
        return 1;
    }

    private void insert(String emailHash, String ipHash, LocalDateTime failedAt, LocalDateTime blockedUntil) {
        jdbc.update("""
                        INSERT INTO login_attempts (email_hash, ip_hash, failed_at, account_blocked_until)
                        VALUES (?, ?, ?, ?)
                        """,
                emailHash,
                ipHash,
                failedAt,
                blockedUntil
        );
    }

    private int countByEmail(String emailHash) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM login_attempts WHERE email_hash = ?", Integer.class,
                emailHash);
    }

    private int countByIp(String ipHash) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM login_attempts WHERE ip_hash = ?", Integer.class, ipHash);
    }

    private String hash(int number) {
        return "%064x".formatted(number);
    }
}
