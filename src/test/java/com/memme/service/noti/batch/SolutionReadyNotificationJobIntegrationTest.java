package com.memme.service.noti.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.memme.repository.noti.NotificationBulkInsertRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:solution-ready-batch-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class SolutionReadyNotificationJobIntegrationTest {

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        String mysqlUrl = System.getenv("MEMME_TEST_MYSQL_URL");
        if (mysqlUrl != null && !mysqlUrl.isBlank()) {
            if (!mysqlUrl.endsWith("/memme_rollback_b_20261024")) {
                throw new IllegalStateException("MySQL integration test is restricted to the rollback test schema");
            }
            String username = System.getenv("DB_USERNAME");
            String password = System.getenv("DB_PASSWORD");
            if (username == null || password == null) {
                throw new IllegalStateException("DB_USERNAME and DB_PASSWORD are required for MySQL integration tests");
            }
            registry.add("spring.datasource.url", () -> mysqlUrl);
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            registry.add("spring.datasource.username", () -> username);
            registry.add("spring.datasource.password", () -> password);
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        }
    }

    private static final LocalDate TARGET_DATE_1K = LocalDate.of(2026, 10, 24);
    private static final LocalDate TARGET_DATE_10K = LocalDate.of(2026, 10, 25);

    @Autowired private JobOperator jobOperator;
    @Autowired @Qualifier("solutionReadyNotificationJob") private Job notificationJob;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private NotificationBulkInsertRepository bulkInsertRepository;

    @MockitoBean
    private SolutionReadyNotificationItemWriter writer;

    @BeforeEach
    void clearDomainData() throws Exception {
        BatchMetadataTestSupport.initializeSchemaIfNeeded(dataSource);
        BatchMetadataTestSupport.clearExecutions(jdbcTemplate);
        if (System.getenv("MEMME_TEST_MYSQL_URL") != null) {
            assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                    .isEqualTo("memme_rollback_b_20261024");
        }
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM solution_bundles");
        jdbcTemplate.update("DELETE FROM notification_preferences");
        jdbcTemplate.update("DELETE FROM stores");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void 두번째_청크에서_실패하면_첫_청크만_남고_같은_JobInstance_재시작으로_천건을_완료한다()
            throws Exception {
        assertFailureAndRestart(1_000, 2, TARGET_DATE_1K);
    }

    @Test
    void 마지막_청크에서_실패하면_구천오백건만_남고_재시작으로_만건을_완료한다()
            throws Exception {
        assertFailureAndRestart(10_000, 20, TARGET_DATE_10K);
    }

    @Test
    void 만건_마지막_청크_실패_직후_재시작_전에_구천오백건을_확인한다() throws Exception {
        seedData(10_000, TARGET_DATE_10K);
        AtomicInteger writerCall = new AtomicInteger();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Chunk<? extends NotificationBulkInsertRepository.NotificationRow> chunk = invocation.getArgument(0);
            bulkInsertRepository.insertAll(new ArrayList<>(chunk.getItems()));
            if (writerCall.incrementAndGet() == 20) {
                throw new IllegalStateException("test failure after 9,500 committed notifications");
            }
            return null;
        }).when(writer).write(any());

        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate("targetDate", TARGET_DATE_10K)
                .toJobParameters();

        JobExecution failed = jobOperator.start(notificationJob, parameters);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(notificationCount()).isEqualTo(9_500);
        assertThat(failed.getStepExecutions()).hasSize(1);
        var stepExecution = failed.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isEqualTo(10_000);
        assertThat(stepExecution.getWriteCount()).isEqualTo(9_500);
        assertThat(stepExecution.getCommitCount()).isEqualTo(19);
        assertThat(stepExecution.getRollbackCount()).isEqualTo(1);
    }

    private void assertFailureAndRestart(int totalBundles, int failingWriterCall, LocalDate targetDate)
            throws Exception {
        seedData(totalBundles, targetDate);
        AtomicInteger writerCall = new AtomicInteger();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Chunk<? extends NotificationBulkInsertRepository.NotificationRow> chunk = invocation.getArgument(0);
            bulkInsertRepository.insertAll(new ArrayList<>(chunk.getItems()));
            if (writerCall.incrementAndGet() == failingWriterCall) {
                throw new IllegalStateException("test failure after bulk insert");
            }
            return null;
        }).when(writer).write(any());

        JobParameters parameters = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters();

        JobExecution failed = jobOperator.start(notificationJob, parameters);

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(batchExecutionCount("FAILED")).isEqualTo(1);
        assertThat(notificationCount()).isEqualTo(totalBundles - 500L);
        assertThat(failed.getStepExecutions()).hasSize(1);
        assertThat(failed.getStepExecutions().iterator().next().getWriteCount()).isEqualTo(totalBundles - 500L);

        JobExecution restarted = jobOperator.start(notificationJob, parameters);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(batchExecutionCount("COMPLETED")).isEqualTo(1);
        assertThat(notificationCount()).isEqualTo(totalBundles);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT user_id, notification_type, notification_key "
                        + "FROM notifications GROUP BY user_id, notification_type, notification_key "
                        + "HAVING COUNT(*) > 1) duplicates", Long.class)).isZero();
    }

    private long notificationCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Long.class);
    }

    private long batchExecutionCount(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM BATCH_JOB_EXECUTION WHERE STATUS = ?", Long.class, status);
    }

    private void seedData(int count, LocalDate targetDate) {
        List<Object[]> users = new ArrayList<>(count);
        List<Object[]> stores = new ArrayList<>(count);
        List<Object[]> preferences = new ArrayList<>(count);
        List<Object[]> bundles = new ArrayList<>(count);
        for (int id = 1; id <= count; id++) {
            users.add(new Object[]{id, "batch-owner-%d@example.com".formatted(id), "encoded-password",
                    "010%08d".formatted(id)});
            stores.add(new Object[]{id, id, "배치 테스트 매장 %d".formatted(id), "06236", "서울시 강남구"});
            preferences.add(new Object[]{id});
            bundles.add(new Object[]{id, id, id, targetDate});
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
