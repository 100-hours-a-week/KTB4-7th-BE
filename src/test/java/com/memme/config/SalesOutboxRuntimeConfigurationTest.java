package com.memme.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;

class SalesOutboxRuntimeConfigurationTest {
    private MockEnvironment env(String role, boolean enabled, boolean dispatch) {
        var env = new MockEnvironment()
                .withProperty("app.sales.outbox.enabled", Boolean.toString(enabled))
                .withProperty("app.sales.outbox.dispatch-enabled", Boolean.toString(dispatch))
                .withProperty("app.sales.storage.type", "s3")
                .withProperty("app.sales.storage.s3.bucket", "shared-test-bucket");
        env.setActiveProfiles(role);
        return env;
    }

    @Test void disabledRuntimeDoesNotProbeDatabase() {
        var source = mock(DataSource.class);
        SalesOutboxRuntimeConfiguration.validate(env("sales-outbox-web", false, false), source);
        SalesOutboxRuntimeConfiguration.validate(env("sales-outbox-batch", false, true), source);
        verifyNoInteractions(source);
    }

    @Test void refusesWebDispatcherEvenBeforeActivation() {
        assertThatThrownBy(() -> SalesOutboxRuntimeConfiguration.validate(
                env("sales-outbox-web", false, true), mock(DataSource.class)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("web/batch");
    }

    @Test void refusesDisabledBatchDispatcherWhenEnabled() {
        assertThatThrownBy(() -> SalesOutboxRuntimeConfiguration.validate(
                env("sales-outbox-batch", true, false), mock(DataSource.class)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("web/batch");
    }

    @Test void refusesBothRolesAndLocalOrMissingSharedBucket() {
        var both = env("sales-outbox-web", true, false);
        both.setActiveProfiles("sales-outbox-web", "sales-outbox-batch");
        assertThatThrownBy(() -> SalesOutboxRuntimeConfiguration.validate(both, mock(DataSource.class)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Exactly one");
        for (var environment : new MockEnvironment[]{
                env("sales-outbox-web", true, false).withProperty("app.sales.storage.type", "local"),
                env("sales-outbox-batch", true, true).withProperty("app.sales.storage.s3.bucket", " ")}) {
            assertThatThrownBy(() -> SalesOutboxRuntimeConfiguration.validate(environment, mock(DataSource.class)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("shared S3");
        }
    }

    private ResultSet row(String uuid, boolean readOnly, boolean superReadOnly, long id) throws Exception {
        var row = mock(ResultSet.class);
        when(row.next()).thenReturn(true);
        when(row.getString(1)).thenReturn(uuid);
        when(row.getBoolean(2)).thenReturn(readOnly);
        when(row.getBoolean(3)).thenReturn(superReadOnly);
        when(row.getLong(4)).thenReturn(id);
        return row;
    }

    private DataSource source(Statement statement) throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        return source;
    }

    @Test void enabledWebAndBatchVerifyWriterAndRestoreConnectionState() throws Exception {
        for (String role : new String[]{"sales-outbox-web", "sales-outbox-batch"}) {
            var statement = mock(Statement.class);
            var first = row("writer", false, false, 10);
            var second = row("writer", false, false, 10);
            when(statement.executeQuery(SalesOutboxRuntimeConfiguration.SERVER_QUERY))
                    .thenReturn(first, second);
            var source = source(statement);
            SalesOutboxRuntimeConfiguration.validate(env(role, true, role.endsWith("batch")), source);
            verify(statement).execute("START TRANSACTION READ WRITE");
            verify(source.getConnection()).rollback();
            verify(source.getConnection()).setAutoCommit(true);
            verify(source.getConnection()).close();
        }
    }

    @Test void rejectsReadOnlyServerOrSwitchedBackendAndRollsBack() throws Exception {
        for (ResultSet invalid : new ResultSet[]{row("writer", true, false, 10),
                row("writer", false, true, 10), row("replica", false, false, 10),
                row("writer", false, false, 11)}) {
            var statement = mock(Statement.class);
            var first = row("writer", false, false, 10);
            when(statement.executeQuery(SalesOutboxRuntimeConfiguration.SERVER_QUERY))
                    .thenReturn(first, invalid);
            var source = source(statement);
            assertThatThrownBy(() -> SalesOutboxRuntimeConfiguration.verifyWriter(source))
                    .isInstanceOf(IllegalStateException.class);
            verify(source.getConnection()).rollback();
            verify(source.getConnection()).setAutoCommit(true);
        }
    }

    @Test void databaseFailureDoesNotExposeCredentials() throws Exception {
        var source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("jdbc:mysql://private?password=secret"));
        assertThatThrownBy(() -> SalesOutboxRuntimeConfiguration.verifyWriter(source))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("password")
                .hasMessageNotContaining("private").hasNoCause();
    }

    @Test void roleProfileFilesLoadAndRegisterStartupGuardWithoutEnablingTheFeature() throws Exception {
        for (String role : new String[]{"sales-outbox-web", "sales-outbox-batch"}) {
            var environment = new MockEnvironment();
            environment.setActiveProfiles(role);
            var propertySource = new YamlPropertySourceLoader().load(role,
                    new ClassPathResource("application-" + role + ".yml")).getFirst();
            environment.getPropertySources().addFirst(propertySource);
            var source = mock(DataSource.class);
            try (var context = new AnnotationConfigApplicationContext()) {
                context.setEnvironment(environment);
                context.registerBean(DataSource.class, () -> source);
                context.register(SalesOutboxRuntimeConfiguration.class);
                context.refresh();
                assertThat(context.containsBean("salesOutboxRuntimeGuard")).isTrue();
                assertThat(environment.getProperty("app.sales.outbox.dispatch-enabled", Boolean.class))
                        .isEqualTo(role.endsWith("batch"));
                assertThat(environment.getProperty("app.sales.outbox.enabled", Boolean.class, false)).isFalse();
                verifyNoInteractions(source);
            }
        }
    }

    @Test void legacyApplicationWithoutRoleProfileHasNoNewStartupGuard() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(SalesOutboxRuntimeConfiguration.class);
            context.refresh();
            assertThat(context.containsBean("salesOutboxRuntimeGuard")).isFalse();
        }
    }
}
