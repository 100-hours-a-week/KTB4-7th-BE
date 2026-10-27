package com.memme.service.sales.outbox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import com.memme.entity.sales.*;
import com.memme.repository.sales.*;
import com.memme.repository.sales.outbox.*;
import com.memme.service.sales.TossPosWorkbookParser;
import com.memme.service.sales.analysis.*;
import com.memme.service.sales.upload.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.awaitility.Awaitility.await;

@EnabledIfEnvironmentVariable(named = "OUTBOX_MYSQL_TEST_URL", matches = ".+/outbox_test.*")
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "app.sales.outbox.enabled=true",
        "app.sales.outbox.dispatch-enabled=false"})
@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SalesOutboxMySqlIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(97000);
    private static final Path STORAGE;
    static { try { STORAGE = Files.createTempDirectory("sales-outbox-mysql-"); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        String url = System.getenv("OUTBOX_MYSQL_TEST_URL");
        if (url == null || !url.startsWith("jdbc:mysql://127.0.0.1:") || !url.contains("/outbox_test?")) {
            throw new IllegalArgumentException("isolated loopback outbox_test DB required");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.sales.storage-directory", STORAGE::toString);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @MockitoSpyBean SalesUploadOutboxRepository jobs;
    @Autowired SalesUploadRepository uploads;
    @Autowired AnalysisRunRepository runs;
    @Autowired SalesUploadService receipt;
    @Autowired SalesUploadOutboxCoordinator coordinator;
    @Autowired SalesUploadOutboxProcessor processor;
    @Autowired SalesUploadWorker worker;
    @Autowired SalesOutboxTelemetry telemetry;
    @Autowired SalesOutboxDatabaseClock clock;
    @Autowired @Qualifier("salesUploadExecutor") ThreadPoolTaskExecutor executor;
    @Autowired TossPosWorkbookParser parser;
    @MockitoSpyBean SalesUploadPersistenceService persistence;
    @MockitoSpyBean SalesAnalysisService analysis;
    @MockitoSpyBean SalesAnalysisSnapshotService snapshots;
    @MockitoBean SalesUploadProcessingService postProcessing;

    @BeforeAll void installActualMigration() throws Exception {
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        jdbc.execute("DROP TABLE sales_upload_outbox");
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    "docker/mysql/migrations/20261025-add-sales-upload-outbox.sql"));
        }
    }
    @BeforeEach void cleanQueue() { jobs.deleteAllInBatch(); }

    @Test void receiptAndOutboxAreAtomicAndBoundToOriginalRun() throws Exception {
        var fixture = fixture();
        var job = job(fixture);
        assertThat(job.getAnalysisRunId()).isEqualTo(fixture.receipt().analysisRunId());
        assertThat(job.getStatus()).isEqualTo(SalesUploadOutboxEntity.Status.READY);
        assertThat(uploads.findById(job.getUploadId()).orElseThrow().getStatus()).isEqualTo(SalesUploadStatus.PENDING);
        long beforeUploads = uploads.count(), beforeRuns = runs.count();
        doThrow(new DataIntegrityViolationException("injected outbox insert failure"))
                .when(jobs).saveAndFlush(any(SalesUploadOutboxEntity.class));
        assertThatThrownBy(() -> receipt.upload(new SalesUploadCommand(fixture.store(), fixture.user(), "sales.xlsx", sample())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(uploads.count()).isEqualTo(beforeUploads);
        assertThat(runs.count()).isEqualTo(beforeRuns);
    }

    @Test void twoDatabaseConnectionsDoNotClaimTheSameJob() throws Exception {
        fixture();
        var pool = Executors.newFixedThreadPool(2);
        var go = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { go.await(); return coordinator.claimNext("node-a"); });
            var second = pool.submit(() -> { go.await(); return coordinator.claimNext("node-b"); });
            go.countDown();
            long count = java.util.stream.Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .filter(java.util.Optional::isPresent).count();
            assertThat(count).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void restartAfterReceiptCanCompleteAndDoneReplayHasNoSideEffects() throws Exception {
        var fixture = fixture();
        // receipt 직후 직접 Worker 호출 없이 DB에 남은 작업을 새 Dispatcher가 읽는 경로.
        var dispatcher = new SalesUploadOutboxDispatcher(coordinator, worker, executor, telemetry, true, true);
        dispatcher.poll();
        await().atMost(java.time.Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(job(fixture).getStatus()).isEqualTo(SalesUploadOutboxEntity.Status.DONE));
        assertThat(uploads.findById(fixture.receipt().uploadId()).orElseThrow().getStatus()).isEqualTo(SalesUploadStatus.COMPLETED);
        assertThat(runs.findById(fixture.receipt().analysisRunId()).orElseThrow().getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() ->
                verify(postProcessing).afterOutboxBasicAnalysis(eq(fixture.receipt().uploadId()), any(), any(), any()));
        assertThat(coordinator.claimNext("restarted")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_analyses WHERE analysis_run_id=?", Integer.class,
                fixture.receipt().analysisRunId())).isEqualTo(1);
    }

    @Test void expiredOwnerCannotWriteAfterAnotherOwnerTakesOver() throws Exception {
        var fixture = fixture();
        var old = coordinator.claimNext("old").orElseThrow();
        expire(old);
        var replacement = coordinator.claimNext("replacement").orElseThrow();
        assertThat(replacement.token()).isNotEqualTo(old.token());
        assertThatThrownBy(() -> coordinator.write(old, job -> {
            jdbc.update("UPDATE sales_uploads SET error_code='STALE_WRITE' WHERE id=?", old.uploadId());
            return null;
        })).isInstanceOf(SalesOutboxOwnershipLostException.class);
        assertThat(uploads.findById(old.uploadId()).orElseThrow().getErrorCode()).isNull();
        assertThat(processor.process(old, () -> true)).isEqualTo("OWNERSHIP_LOST");
        assertThat(processor.process(replacement, () -> true)).isEqualTo("DONE");
        assertThat(job(fixture).getAttemptCount()).isEqualTo(2);
    }

    @Test void transientAnalysisFailureResumesWithoutReplacingAppliedSalesAgain() throws Exception {
        var fixture = fixture();
        doThrow(new TransientDataAccessResourceException("temporary analysis read"))
                .when(analysis).analyze(any(), anyString(), any(), any());
        assertThat(processor.process(coordinator.claimNext("first").orElseThrow(), () -> true)).isEqualTo("READY");
        assertThat(job(fixture).getCheckpoint()).isEqualTo(SalesUploadOutboxEntity.Checkpoint.DATA_APPLIED);
        assertThat(uploads.findById(fixture.receipt().uploadId()).orElseThrow().getStatus()).isEqualTo(SalesUploadStatus.PROCESSING);
        assertThat(coordinator.claimNext("too-early")).isEmpty();
        doCallRealMethod().when(analysis).analyze(any(), anyString(), any(), any());
        due(job(fixture).getId());
        assertThat(processor.process(coordinator.claimNext("retry").orElseThrow(), () -> true)).isEqualTo("DONE");
        verify(persistence, times(1)).replaceCoverage(eq(fixture.receipt().uploadId()), any(), any());
        assertThat(job(fixture).getAttemptCount()).isEqualTo(2);
    }

    @Test void dataAndCheckpointRollbackTogether() throws Exception {
        var fixture = fixture(); var claim = coordinator.claimNext("first").orElseThrow();
        var data = parser.parse(new java.io.ByteArrayInputStream(sample()));
        assertThatThrownBy(() -> coordinator.write(claim, job -> {
            persistence.replaceCoverage(job.getUploadId(), job.getStoreId(), data);
            job.dataApplied(data.items().size(), clock.now());
            throw new TransientDataAccessResourceException("crash before data checkpoint commit");
        })).isInstanceOf(TransientDataAccessResourceException.class);
        assertThat(job(fixture).getCheckpoint()).isEqualTo(SalesUploadOutboxEntity.Checkpoint.ACCEPTED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_orders WHERE store_id=?", Integer.class, fixture.store())).isZero();
    }

    @Test void snapshotCompletionAndDoneRollbackTogether() throws Exception {
        var fixture = fixture();
        doThrow(new TransientDataAccessResourceException("pause after data commit"))
                .when(analysis).analyze(any(), anyString(), any(), any());
        processor.process(coordinator.claimNext("first").orElseThrow(), () -> true);
        doCallRealMethod().when(analysis).analyze(any(), anyString(), any(), any());
        due(job(fixture).getId()); var claim = coordinator.claimNext("retry").orElseThrow();
        var data = parser.parse(new java.io.ByteArrayInputStream(sample()));
        var result = analysis.analyze(fixture.store(), "CUSTOM", data.periodStart(), data.periodEnd());
        assertThatThrownBy(() -> coordinator.write(claim, job -> {
            snapshots.persistForRun(job.getAnalysisRunId(), result);
            uploads.findById(job.getUploadId()).orElseThrow().complete(job.getAppliedRecordCount());
            runs.findById(job.getAnalysisRunId()).orElseThrow().complete(); job.complete(clock.now());
            throw new TransientDataAccessResourceException("crash before completed commit");
        })).isInstanceOf(TransientDataAccessResourceException.class);
        assertThat(job(fixture).getStatus()).isEqualTo(SalesUploadOutboxEntity.Status.RUNNING);
        assertThat(job(fixture).getCheckpoint()).isEqualTo(SalesUploadOutboxEntity.Checkpoint.DATA_APPLIED);
        assertThat(uploads.findById(claim.uploadId()).orElseThrow().getStatus()).isEqualTo(SalesUploadStatus.PROCESSING);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_analyses WHERE analysis_run_id=?", Integer.class, claim.analysisRunId())).isZero();
    }

    @Test void sameStoreWaitsForRetryAndOtherStoreCanProgress() throws Exception {
        var first = fixture();
        var later = receipt.upload(new SalesUploadCommand(first.store(), first.user(), "later.xlsx", sample()));
        var other = fixture(); var claim = coordinator.claimNext("first").orElseThrow();
        coordinator.failed(claim, new TransientDataAccessResourceException("retry"));
        var independent = coordinator.claimNext("other").orElseThrow();
        assertThat(independent.uploadId()).isEqualTo(other.receipt().uploadId());
        assertThat(independent.uploadId()).isNotEqualTo(later.uploadId());
        assertThat(coordinator.claimNext("blocked")).isEmpty();
    }

    @Test void attemptLimitMakesUploadAndOriginalRunFailedAtomically() throws Exception {
        var fixture = fixture();
        for (int attempt = 1; attempt <= 5; attempt++) {
            var claim = coordinator.claimNext("node-" + attempt).orElseThrow();
            assertThat(job(fixture).getAttemptCount()).isEqualTo(attempt); expire(claim);
        }
        assertThat(coordinator.claimNext("exhausted")).isEmpty();
        assertThat(job(fixture).getStatus()).isEqualTo(SalesUploadOutboxEntity.Status.DEAD);
        assertThat(uploads.findById(fixture.receipt().uploadId()).orElseThrow().getStatus()).isEqualTo(SalesUploadStatus.FAILED);
        assertThat(runs.findById(fixture.receipt().analysisRunId()).orElseThrow().getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
    }

    @Test void invalidInputIsTerminalAndRejectionDoesNotSpendAnAttempt() throws Exception {
        var fixture = fixture(); var claim = coordinator.claimNext("node").orElseThrow();
        coordinator.rejected(claim);
        assertThat(job(fixture).getAttemptCount()).isZero();
        assertThat(job(fixture).getStatus()).isEqualTo(SalesUploadOutboxEntity.Status.READY);
        due(job(fixture).getId());
        var replacement = coordinator.claimNext("node-2").orElseThrow();
        assertThat(coordinator.failed(replacement, new IllegalArgumentException("invalid contents")))
                .isEqualTo(SalesUploadOutboxEntity.Status.DEAD);
    }

    @Test void saturatedExecutorLeavesReceiptInDatabase() throws Exception {
        var fixture = fixture(); var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        executor.execute(() -> { started.countDown(); try { release.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); } });
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            new SalesUploadOutboxDispatcher(coordinator, worker, executor, telemetry, true, true).poll();
            assertThat(job(fixture).getAttemptCount()).isZero();
            assertThat(job(fixture).getStatus()).isEqualTo(SalesUploadOutboxEntity.Status.READY);
        } finally { release.countDown(); }
    }

    @Test void actualMigrationEnforcesUniquenessAndLeaseConstraints() throws Exception {
        var fixture = fixture(); var job = job(fixture);
        assertThatThrownBy(() -> jdbc.update("UPDATE sales_upload_outbox SET status='RUNNING' WHERE id=?", job.getId()))
                .isInstanceOf(DataAccessException.class)
                .satisfies(error -> {
                    var cause = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(error);
                    assertThat(cause).isInstanceOf(java.sql.SQLException.class);
                    assertThat(((java.sql.SQLException) cause).getErrorCode()).isEqualTo(3819);
                });
        assertThatThrownBy(() -> jobs.saveAndFlush(SalesUploadOutboxEntity.ready(fixture.store(),
                fixture.receipt().uploadId(), fixture.receipt().analysisRunId(), clock.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void utcLeaseAndAvailabilityAreIndependentOfJvmTimezone() throws Exception {
        var fixture = fixture();
        long seconds = jdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND, available_at, UTC_TIMESTAMP(6))) FROM sales_upload_outbox WHERE id=?",
                Long.class, job(fixture).getId());
        assertThat(seconds).isLessThan(5);
        coordinator.claimNext("utc").orElseThrow();
        long lease = jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(6), lease_until) FROM sales_upload_outbox WHERE id=?",
                Long.class, job(fixture).getId());
        assertThat(lease).isBetween(55L, 60L);
    }

    @Test void forcedJvmTerminationRecoversCommittedClaimAndRollsBackUncommittedCheckpoint() throws Exception {
        for (String mode : new String[]{"after-claim", "uncommitted-checkpoint"}) {
            jobs.deleteAllInBatch();
            var fixture = fixture();
            Path output = Files.createTempFile("outbox-crash-probe-", ".log");
            var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("outbox.test.runtime-classpath"), SalesOutboxCrashProbe.class.getName(), mode)
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            try {
                assertThat(child.waitFor(40, TimeUnit.SECONDS)).withFailMessage("Probe timeout; see %s", output).isTrue();
                assertThat(child.exitValue()).withFailMessage("Probe failed: %s", Files.readString(output))
                        .isEqualTo(mode.equals("after-claim") ? 73 : 74);
            } finally { child.destroyForcibly(); }
            assertThat(job(fixture).getStatus()).isEqualTo(SalesUploadOutboxEntity.Status.RUNNING);
            assertThat(job(fixture).getCheckpoint()).isEqualTo(SalesUploadOutboxEntity.Checkpoint.ACCEPTED);
            jdbc.update("UPDATE sales_upload_outbox SET lease_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?", job(fixture).getId());
            var recovered = coordinator.claimNext("after-restart").orElseThrow();
            assertThat(processor.process(recovered, () -> true)).isEqualTo("DONE");
        }
    }

    @Test void originalRunIsUsedEvenIfAnotherRunWasCreatedForTheUpload() throws Exception {
        var fixture = fixture();
        var later = runs.saveAndFlush(AnalysisRunEntity.pending(fixture.store(), fixture.user(), fixture.receipt().uploadId()));
        var claim = coordinator.claimNext("original").orElseThrow();
        assertThat(processor.process(claim, () -> true)).isEqualTo("DONE");
        assertThat(runs.findById(later.getId()).orElseThrow().getStatus()).isEqualTo(AnalysisRunStatus.PENDING);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_analyses WHERE analysis_run_id=?", Integer.class,
                fixture.receipt().analysisRunId())).isEqualTo(1);
        clearInvocations(persistence, postProcessing);
        assertThat(processor.process(claim, () -> true)).isEqualTo("OWNERSHIP_LOST");
        verifyNoInteractions(persistence, postProcessing);
    }

    @Test void lostHeartbeatStopsTheNextWriteAndLeaseCanRecover() throws Exception {
        var fixture = fixture(); var claim = coordinator.claimNext("heartbeat").orElseThrow();
        var healthy = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod(); healthy.set(false); return result;
        }).when(analysis).analyze(any(), anyString(), any(), any());
        assertThat(processor.process(claim, healthy::get)).isEqualTo("OWNERSHIP_LOST");
        assertThat(job(fixture).getCheckpoint()).isEqualTo(SalesUploadOutboxEntity.Checkpoint.DATA_APPLIED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_analyses WHERE analysis_run_id=?", Integer.class, claim.analysisRunId())).isZero();
        expire(claim);
        assertThatThrownBy(() -> coordinator.heartbeat(claim)).isInstanceOf(SalesOutboxOwnershipLostException.class);
        doCallRealMethod().when(analysis).analyze(any(), anyString(), any(), any());
        assertThat(processor.process(coordinator.claimNext("recovery").orElseThrow(), () -> true)).isEqualTo("DONE");
    }

    @Test void dispatcherRegistrationRejectionKeepsDurableJobReady() throws Exception {
        var fixture = fixture(); var rejectedWorker = mock(SalesUploadWorker.class);
        doThrow(new RejectedExecutionException("registration race"))
                .when(rejectedWorker).processOutbox(any(), any());
        new SalesUploadOutboxDispatcher(coordinator, rejectedWorker, executor, telemetry, true, true).poll();
        assertThat(job(fixture).getStatus()).isEqualTo(SalesUploadOutboxEntity.Status.READY);
        assertThat(job(fixture).getAttemptCount()).isZero();
        assertThat(uploads.findById(fixture.receipt().uploadId()).orElseThrow().getStatus()).isEqualTo(SalesUploadStatus.PENDING);
    }

    @Test void heartbeatExtendsTheLeaseAndQueueObservationDoesNotNeedHighCardinalityTags() throws Exception {
        var fixture = fixture(); var claim = coordinator.claimNext("heartbeat").orElseThrow();
        jdbc.update("UPDATE sales_upload_outbox SET lease_until=UTC_TIMESTAMP(6)+INTERVAL 5 SECOND WHERE id=?", claim.id());
        coordinator.heartbeat(claim);
        assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(6),lease_until) FROM sales_upload_outbox WHERE id=?",
                Long.class, claim.id())).isBetween(55L,60L);
        var observation = new SalesUploadOutboxObservability(jdbc, coordinator, true).snapshot();
        assertThat(observation.get("RUNNING")).isEqualTo(1);
        assertThat(observation).doesNotContainKeys("uploadId", "storeId", "userId", "fileName");
    }

    @Test void recordQueueQueryPlanWithActualIndexes() throws Exception {
        fixture();
        var plan = jdbc.queryForList("""
                EXPLAIN SELECT j.* FROM sales_upload_outbox j
                WHERE ((j.status='READY' AND j.available_at<=UTC_TIMESTAMP(6))
                    OR (j.status='RUNNING' AND j.lease_until<=UTC_TIMESTAMP(6)))
                AND NOT EXISTS (SELECT 1 FROM sales_upload_outbox previous
                    WHERE previous.store_id=j.store_id AND previous.id<j.id AND previous.status IN ('READY','RUNNING'))
                ORDER BY j.available_at,j.id LIMIT 32
                """);
        assertThat(plan).isNotEmpty();
        Files.createDirectories(Path.of("build/reports/outbox"));
        Files.writeString(Path.of("build/reports/outbox/mysql-query-plan.txt"), plan.toString());
    }

    private Fixture fixture() throws Exception {
        long user = IDS.incrementAndGet(), store = IDS.incrementAndGet();
        jdbc.update("INSERT INTO users(id,email,password_hash,phone,created_at,updated_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                user, user + "@outbox.example", "test-only", String.format("010%08d", user));
        jdbc.update("INSERT INTO stores(id,owner_user_id,business_registration_no,name,postal_code,address,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                store, user, String.format("%010d", store), "outbox test", "01234", "test", "ACTIVE");
        return new Fixture(user, store, receipt.upload(new SalesUploadCommand(store, user, "sales.xlsx", sample())));
    }
    private byte[] sample() {
        try (var input = getClass().getResourceAsStream("/sales/toss-pos/sample-normal.xlsx")) { return input.readAllBytes(); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private SalesUploadOutboxEntity job(Fixture fixture) {
        Long id = jdbc.queryForObject("SELECT id FROM sales_upload_outbox WHERE upload_id=?", Long.class, fixture.receipt().uploadId());
        return jobs.findById(id).orElseThrow();
    }
    private void due(Long id) { jdbc.update("UPDATE sales_upload_outbox SET available_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?", id); }
    private void expire(SalesUploadOutboxClaim claim) { jdbc.update("UPDATE sales_upload_outbox SET lease_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?", claim.id()); }
    private record Fixture(long user, long store, SalesUploadReceipt receipt) {}
}
