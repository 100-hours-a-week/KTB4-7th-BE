package com.memme.repository.auth;

import com.memme.service.auth.LoginRateLimitPolicy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoginRateLimitRepository {

    private static final int LOCK_TIMEOUT_SECONDS = 3;
    private static final String RECENT_ACCOUNT_FAILURES_SQL = """
            SELECT failed_at, account_blocked_until
            FROM login_attempts
            WHERE email_hash = ? AND failed_at > ?
            ORDER BY failed_at DESC
            LIMIT ?
            """;
    private static final String RECENT_IP_FAILURES_SQL = """
            SELECT failed_at
            FROM login_attempts
            WHERE ip_hash = ? AND failed_at > ?
            ORDER BY failed_at DESC
            LIMIT ?
            """;
    private static final String INSERT_FAILURE_SQL = """
            INSERT INTO login_attempts (email_hash, ip_hash, failed_at, account_blocked_until)
            VALUES (?, ?, ?, ?)
            """;
    private static final String CLEAR_ACCOUNT_FAILURES_SQL = """
            UPDATE login_attempts
            SET email_hash = NULL, account_blocked_until = NULL
            WHERE email_hash = ?
            """;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final LoginRateLimitPolicy policy;

    public LoginRateLimitRepository(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            LoginRateLimitPolicy policy
    ) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.policy = policy;
    }

    public Optional<LocalDateTime> checkAllowed(String emailHash, String ipHash, LocalDateTime now) {
        return withLocks(emailHash, ipHash, connection -> findRetryAt(connection, emailHash, ipHash, now));
    }

    public Optional<LocalDateTime> recordFailureIfAllowed(
            String emailHash,
            String ipHash,
            LocalDateTime now
    ) {
        return withLocks(emailHash, ipHash, connection -> {
            Optional<LocalDateTime> retryAt = findRetryAt(connection, emailHash, ipHash, now);
            if (retryAt.isPresent()) {
                return retryAt;
            }

            List<LoginRateLimitPolicy.AccountFailure> accountFailures = findRecentAccountFailures(
                    connection, emailHash, now
            );
            LocalDateTime blockedUntil = policy.cooldownAfterFailureCount(accountFailures.size() + 1)
                    .map(now::plus)
                    .orElse(null);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_FAILURE_SQL)) {
                statement.setString(1, emailHash);
                statement.setString(2, ipHash);
                statement.setObject(3, now);
                statement.setObject(4, blockedUntil);
                statement.executeUpdate();
            }
            return Optional.empty();
        });
    }

    public Optional<LocalDateTime> clearAccountFailuresIfAllowed(
            String emailHash,
            String ipHash,
            LocalDateTime now
    ) {
        return withLocks(emailHash, ipHash, connection -> {
            Optional<LocalDateTime> retryAt = findRetryAt(connection, emailHash, ipHash, now);
            if (retryAt.isPresent()) {
                return retryAt;
            }
            try (PreparedStatement statement = connection.prepareStatement(CLEAR_ACCOUNT_FAILURES_SQL)) {
                statement.setString(1, emailHash);
                statement.executeUpdate();
            }
            return Optional.empty();
        });
    }

    public int deleteExpiredBefore(LocalDateTime cutoff) {
        return jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    DELETE FROM login_attempts
                    WHERE failed_at <= ?
                    ORDER BY failed_at ASC
                    LIMIT 10000
                    """);
            statement.setObject(1, cutoff);
            return statement;
        });
    }

    private Optional<LocalDateTime> findRetryAt(
            Connection connection,
            String emailHash,
            String ipHash,
            LocalDateTime now
    ) throws SQLException {
        return policy.retryAt(
                findRecentAccountFailures(connection, emailHash, now),
                findRecentIpFailures(connection, ipHash, now),
                now
        );
    }

    private List<LoginRateLimitPolicy.AccountFailure> findRecentAccountFailures(
            Connection connection,
            String emailHash,
            LocalDateTime now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RECENT_ACCOUNT_FAILURES_SQL)) {
            statement.setString(1, emailHash);
            statement.setObject(2, now.minusMinutes(LoginRateLimitPolicy.ACCOUNT_WINDOW_MINUTES));
            statement.setInt(3, LoginRateLimitPolicy.ACCOUNT_ATTEMPT_LOOKUP_LIMIT);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<LoginRateLimitPolicy.AccountFailure> failures = new ArrayList<>();
                while (resultSet.next()) {
                    failures.add(new LoginRateLimitPolicy.AccountFailure(
                            resultSet.getObject("failed_at", LocalDateTime.class),
                            resultSet.getObject("account_blocked_until", LocalDateTime.class)
                    ));
                }
                return failures;
            }
        }
    }

    private List<LocalDateTime> findRecentIpFailures(
            Connection connection,
            String ipHash,
            LocalDateTime now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RECENT_IP_FAILURES_SQL)) {
            statement.setString(1, ipHash);
            statement.setObject(2, now.minusMinutes(LoginRateLimitPolicy.IP_WINDOW_MINUTES));
            statement.setInt(3, LoginRateLimitPolicy.IP_ATTEMPT_LOOKUP_LIMIT);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<LocalDateTime> failures = new ArrayList<>();
                while (resultSet.next()) {
                    failures.add(resultSet.getObject("failed_at", LocalDateTime.class));
                }
                return failures;
            }
        }
    }

    private <T> T withLocks(String emailHash, String ipHash, LockedWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            // Named locks are connection-scoped. Autocommit makes each write visible before unlock.
            connection.setAutoCommit(true);
            List<String> lockNames = new TreeSet<>(List.of(
                    namedLock("e", emailHash),
                    namedLock("i", ipHash)
            )).stream().toList();
            List<String> acquiredLocks = new ArrayList<>(lockNames.size());
            try {
                acquireLocks(connection, lockNames, acquiredLocks);
                return work.run(connection);
            } finally {
                releaseLocks(connection, acquiredLocks);
            }
        } catch (SQLException exception) {
            throw new DataAccessResourceFailureException("로그인 요청 제한 정보를 처리하지 못했습니다.", exception);
        }
    }

    private void acquireLocks(Connection connection, List<String> lockNames, List<String> acquiredLocks)
            throws SQLException {
        for (String lockName : lockNames) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
                statement.setString(1, lockName);
                statement.setInt(2, LOCK_TIMEOUT_SECONDS);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next() || resultSet.getInt(1) != 1 || resultSet.wasNull()) {
                        throw new SQLException("로그인 요청 제한 잠금을 획득하지 못했습니다.");
                    }
                    acquiredLocks.add(lockName);
                }
            }
        }
    }

    private void releaseLocks(Connection connection, List<String> acquiredLocks) throws SQLException {
        SQLException releaseFailure = null;
        for (int index = acquiredLocks.size() - 1; index >= 0; index--) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, acquiredLocks.get(index));
                statement.executeQuery().close();
            } catch (SQLException exception) {
                if (releaseFailure == null) {
                    releaseFailure = exception;
                } else {
                    releaseFailure.addSuppressed(exception);
                }
            }
        }
        if (releaseFailure != null) {
            try {
                connection.abort(Runnable::run);
            } catch (SQLException | RuntimeException abortFailure) {
                releaseFailure.addSuppressed(abortFailure);
            }
            throw releaseFailure;
        }
    }

    private String namedLock(String type, String keyHash) {
        // MySQL limits named lock identifiers to 64 characters.
        return "lr:" + type + ":" + keyHash.substring(0, 59);
    }

    @FunctionalInterface
    private interface LockedWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
