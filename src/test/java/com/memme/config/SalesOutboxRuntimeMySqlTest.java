package com.memme.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "OUTBOX_RUNTIME_MYSQL_URL", matches = ".+/outbox_runtime_test.*")
class SalesOutboxRuntimeMySqlTest {
    private static DriverManagerDataSource source;

    @BeforeAll static void isolatedDatabase() {
        String url = System.getenv("OUTBOX_RUNTIME_MYSQL_URL");
        if (!url.startsWith("jdbc:mysql://127.0.0.1:") || !url.contains("/outbox_runtime_test?")) {
            throw new IllegalArgumentException("Dedicated loopback outbox_runtime_test database required");
        }
        source = new DriverManagerDataSource(url, "root", "");
        assertThat(new JdbcTemplate(source).queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
    }

    @Test void realWriterProbeLeavesConnectionAndDatabaseUsable() throws Exception {
        SalesOutboxRuntimeConfiguration.verifyWriter(source);
        try (var connection = source.getConnection()) {
            assertThat(connection.getAutoCommit()).isTrue();
            assertThat(new JdbcTemplate(source).queryForObject("SELECT @@read_only", Integer.class)).isZero();
        }
    }

    @Test void refusesEvenPrivilegedConnectionToReadOnlyServer() {
        var jdbc = new JdbcTemplate(source);
        try {
            jdbc.execute("SET GLOBAL read_only=ON");
            assertThatThrownBy(() -> SalesOutboxRuntimeConfiguration.verifyWriter(source))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            jdbc.execute("SET GLOBAL read_only=OFF");
        }
        SalesOutboxRuntimeConfiguration.verifyWriter(source);
    }
}
