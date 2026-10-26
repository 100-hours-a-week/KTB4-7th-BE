package com.memme.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile({"sales-outbox-web", "sales-outbox-batch"})
public class SalesOutboxRuntimeConfiguration {
    static final String SERVER_QUERY = "SELECT @@server_uuid, @@read_only, @@super_read_only, CONNECTION_ID()";

    @Bean
    InitializingBean salesOutboxRuntimeGuard(Environment environment, DataSource source) {
        return () -> validate(environment, source);
    }

    static void validate(Environment environment, DataSource source) {
        var profiles = Arrays.asList(environment.getActiveProfiles());
        boolean web = profiles.contains("sales-outbox-web");
        boolean batch = profiles.contains("sales-outbox-batch");
        if (web == batch) {
            throw new IllegalStateException("Exactly one sales Outbox runtime profile is required");
        }
        boolean enabled = environment.getProperty("app.sales.outbox.enabled", Boolean.class, false);
        boolean dispatch = environment.getProperty("app.sales.outbox.dispatch-enabled", Boolean.class, true);
        if (web && dispatch || enabled && batch && !dispatch) {
            throw new IllegalStateException("Sales Outbox dispatcher does not match the web/batch role");
        }
        if (!enabled) return;
        if (!"s3".equals(environment.getProperty("app.sales.storage.type", "local"))
                || environment.getProperty("app.sales.storage.s3.bucket", "").isBlank()) {
            throw new IllegalStateException("Enabled ECS sales Outbox requires a shared S3 bucket");
        }
        verifyWriter(source);
    }

    // Startup preflight only. ProxySQL routing and failover still need deployment verification.
    static void verifyWriter(DataSource source) {
        try (Connection connection = source.getConnection()) {
            if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName()) || connection.isReadOnly()) {
                throw new IllegalStateException("Sales Outbox requires a MySQL writer connection");
            }
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(5);
                statement.execute("START TRANSACTION READ WRITE");
                Server first = readServer(statement);
                Server second = readServer(statement);
                if (!first.equals(second) || first.readOnly() || first.superReadOnly()
                        || first.uuid() == null || first.uuid().isBlank()) {
                    throw new IllegalStateException("Sales Outbox DB route must stay on one writable Primary");
                }
            } finally {
                try { connection.rollback(); }
                finally { connection.setAutoCommit(autoCommit); }
            }
        } catch (SQLException failure) {
            // Do not expose JDBC URLs, credentials, or proxy response details in startup errors.
            throw new IllegalStateException("Sales Outbox writer preflight failed; inspect the DB route");
        }
    }

    private static Server readServer(Statement statement) throws SQLException {
        try (ResultSet row = statement.executeQuery(SERVER_QUERY)) {
            if (!row.next()) throw new SQLException("Missing DB server identity");
            return new Server(row.getString(1), row.getBoolean(2), row.getBoolean(3), row.getLong(4));
        }
    }

    private record Server(String uuid, boolean readOnly, boolean superReadOnly, long connectionId) {}
}
