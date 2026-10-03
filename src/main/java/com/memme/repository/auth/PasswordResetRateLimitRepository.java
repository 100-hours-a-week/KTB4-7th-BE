package com.memme.repository.auth;

import com.memme.service.auth.PasswordResetRateLimitPolicy;
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
public class PasswordResetRateLimitRepository {

    private static final int LOCK_TIMEOUT_SECONDS = 3;
    private static final int EMAIL_ATTEMPT_LOOKUP_LIMIT = PasswordResetRateLimitPolicy.EMAIL_DAILY_LIMIT;
    private static final int IP_ATTEMPT_LOOKUP_LIMIT = PasswordResetRateLimitPolicy.IP_HOURLY_LIMIT;
    private static final String RECENT_EMAIL_ATTEMPTS_SQL = """
            SELECT requested_at
            FROM password_reset_email_attempts
            WHERE email_hash = ? AND requested_at > ?
            ORDER BY requested_at DESC
            LIMIT ?
            """;
    private static final String RECENT_IP_ATTEMPTS_SQL = """
            SELECT requested_at
            FROM password_reset_email_attempts
            WHERE ip_hash = ? AND requested_at > ?
            ORDER BY requested_at DESC
            LIMIT ?
            """;
    private static final String INSERT_ATTEMPT_SQL = """
            INSERT INTO password_reset_email_attempts (email_hash, ip_hash, requested_at)
            VALUES (?, ?, ?)
            """;

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordResetRateLimitPolicy policy;

    public PasswordResetRateLimitRepository(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            PasswordResetRateLimitPolicy policy
    ) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.policy = policy;
    }

    public Optional<LocalDateTime> recordIfAllowed(
            String emailHash,
            String ipHash,
            LocalDateTime now
    ) {
        try (Connection connection = dataSource.getConnection()) {
            // MySQL named locks belong to this connection; autocommit makes the event visible before unlock.
            connection.setAutoCommit(true);
            List<String> lockNames = new TreeSet<>(List.of(
                    namedLock("e", emailHash),
                    namedLock("i", ipHash)
            )).stream().toList();
            List<String> acquiredLocks = new ArrayList<>(lockNames.size());
            try {
                acquireLocks(connection, lockNames, acquiredLocks);
                Optional<LocalDateTime> retryAt = policy.retryAt(
                        findRecentAttempts(connection, RECENT_EMAIL_ATTEMPTS_SQL,
                                emailHash, now.minusHours(24), EMAIL_ATTEMPT_LOOKUP_LIMIT),
                        findRecentAttempts(connection, RECENT_IP_ATTEMPTS_SQL,
                                ipHash, now.minusHours(1), IP_ATTEMPT_LOOKUP_LIMIT),
                        now
                );
                if (retryAt.isPresent()) {
                    return retryAt;
                }
                insertAttempt(connection, emailHash, ipHash, now);
                return Optional.empty();
            } finally {
                releaseLocks(connection, acquiredLocks);
            }
        } catch (SQLException exception) {
            throw new DataAccessResourceFailureException(
                    "비밀번호 재설정 요청 제한 정보를 처리하지 못했습니다.", exception
            );
        }
    }

    public int deleteExpiredBefore(LocalDateTime cutoff) {
        return jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    DELETE FROM password_reset_email_attempts
                    WHERE requested_at <= ?
                    ORDER BY requested_at ASC
                    LIMIT 10000
                    """);
            statement.setObject(1, cutoff);
            return statement;
        });
    }

    private List<LocalDateTime> findRecentAttempts(
            Connection connection,
            String sql,
            String keyHash,
            LocalDateTime cutoff,
            int limit
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, keyHash);
            statement.setObject(2, cutoff);
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<LocalDateTime> attempts = new ArrayList<>();
                while (resultSet.next()) {
                    attempts.add(resultSet.getObject("requested_at", LocalDateTime.class));
                }
                return attempts;
            }
        }
    }

    private void insertAttempt(
            Connection connection,
            String emailHash,
            String ipHash,
            LocalDateTime requestedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ATTEMPT_SQL)) {
            statement.setString(1, emailHash);
            statement.setString(2, ipHash);
            statement.setObject(3, requestedAt);
            statement.executeUpdate();
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
                        throw new SQLException("비밀번호 재설정 요청 제한 잠금을 획득하지 못했습니다.");
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
        return "pr:" + type + ":" + keyHash.substring(0, 59);
    }
}
