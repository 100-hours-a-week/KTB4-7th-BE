package com.memme.service.noti.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.memme.dto.noti.NotificationSseEvent;
import com.memme.repository.noti.NotificationBulkInsertRepository;
import com.memme.service.noti.NotificationSseConnectionService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:solution-ready-sse-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class SolutionReadyNotificationSseIntegrationTest {

    @Autowired private JobOperator jobOperator;
    @Autowired @Qualifier("solutionReadyNotificationJob") private Job job;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @MockitoSpyBean private NotificationBulkInsertRepository bulkInsertRepository;
    @MockitoBean private NotificationSseConnectionService connections;

    @BeforeEach
    void resetDatabase() throws Exception {
        BatchMetadataTestSupport.initializeSchemaIfNeeded(dataSource);
        BatchMetadataTestSupport.clearExecutions(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM solution_bundles");
        jdbcTemplate.update("DELETE FROM notification_preferences");
        jdbcTemplate.update("DELETE FROM stores");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void 실패한_청크는_발행하지_않고_재시작하면_남은_오백건만_발행한다() throws Exception {
        seedData(1_000);
        AtomicInteger insertCalls = new AtomicInteger();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (insertCalls.incrementAndGet() == 2) {
                throw new IllegalStateException("second chunk failed after insert");
            }
            return result;
        }).when(bulkInsertRepository).insertAll(anyList());
        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate("targetDate", LocalDate.of(2026, 10, 26))
                .toJobParameters();

        JobExecution failed = jobOperator.start(job, parameters);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(notificationCount()).isEqualTo(500);
        verify(connections, times(500)).sendToUser(anyLong(), any(NotificationSseEvent.class));

        JobExecution restarted = jobOperator.start(job, parameters);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(notificationCount()).isEqualTo(1_000);
        ArgumentCaptor<NotificationSseEvent> events = ArgumentCaptor.forClass(NotificationSseEvent.class);
        verify(connections, times(1_000)).sendToUser(anyLong(), events.capture());
        assertThat(events.getAllValues()).extracting(event -> event.data().id()).doesNotHaveDuplicates();
        assertThat(restarted.getStepExecutions().iterator().next().getWriteCount()).isEqualTo(500);
    }

    private long notificationCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Long.class);
    }

    private void seedData(int count) {
        List<Object[]> users = new ArrayList<>(count);
        List<Object[]> stores = new ArrayList<>(count);
        List<Object[]> preferences = new ArrayList<>(count);
        List<Object[]> bundles = new ArrayList<>(count);
        for (int id = 1; id <= count; id++) {
            users.add(new Object[]{id, "sse-owner-%d@example.com".formatted(id), "encoded-password",
                    "010%08d".formatted(id)});
            stores.add(new Object[]{id, id, "SSE 테스트 매장 %d".formatted(id), "06236", "서울시 강남구"});
            preferences.add(new Object[]{id});
            bundles.add(new Object[]{id, id, id, LocalDate.of(2026, 10, 26)});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO users (id, email, password_hash, phone, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", users);
        jdbcTemplate.batchUpdate(
                "INSERT INTO stores (id, owner_user_id, name, postal_code, address, status, menu_revision, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                stores);
        jdbcTemplate.batchUpdate(
                "INSERT INTO notification_preferences (user_id, solution_enabled, sales_upload_reminder_enabled, "
                        + "ranking_change_enabled, created_at, updated_at) "
                        + "VALUES (?, TRUE, TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", preferences);
        jdbcTemplate.batchUpdate(
                "INSERT INTO solution_bundles (id, store_id, sales_analysis_id, target_date, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", bundles);
    }
}
