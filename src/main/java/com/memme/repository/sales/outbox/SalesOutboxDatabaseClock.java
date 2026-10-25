package com.memme.repository.sales.outbox;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SalesOutboxDatabaseClock {
    private final JdbcTemplate jdbc;
    public SalesOutboxDatabaseClock(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public LocalDateTime now() {
        return jdbc.execute((ConnectionCallback<LocalDateTime>) connection -> {
            String sql = connection.getMetaData().getDatabaseProductName().equals("H2")
                    ? "SELECT CAST(CURRENT_TIMESTAMP AT TIME ZONE 'UTC' AS TIMESTAMP)"
                    : "SELECT UTC_TIMESTAMP(6)";
            try (var statement = connection.prepareStatement(sql); var result = statement.executeQuery()) {
                result.next(); return result.getObject(1, LocalDateTime.class);
            }
        });
    }
}
