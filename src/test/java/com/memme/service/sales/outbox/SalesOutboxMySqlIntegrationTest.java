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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest(name = "A={0}~{1}, B={2}~{3}")
    @CsvSource({"1,10,5,15", "1,10,1,10", "1,15,5,10", "5,10,1,15", "1,4,5,10", "1,5,5,10"})
    void overlappingCoverageKeepsOnlyLaterOrdersAndPreservesOutsideAndOtherStore(
            int firstStart, int firstEnd, int laterStart, int laterEnd) throws Exception {
        var baseline = fixture(SalesOverlapWorkbook.daily(1, 18, 500));
        completeNext(baseline.receipt());
        var other = fixture(SalesOverlapWorkbook.daily(1, 18, 777));
        completeNext(other.receipt());
        var otherBefore = databaseState(other.store());
        var first = enqueue(baseline, SalesOverlapWorkbook.daily(firstStart, firstEnd, 1000));
        var later = enqueue(baseline, SalesOverlapWorkbook.daily(laterStart, laterEnd, 2000));

        completeNext(first);
        assertSnapshot(first, (firstEnd - firstStart + 1) * 1000L, firstEnd - firstStart + 1);
        completeNext(later);

        var expected = days(baseline.receipt(), 1, 18, 500);
        replaceExpected(expected, first, firstStart, firstEnd, 1000);
        replaceExpected(expected, later, laterStart, laterEnd, 2000);
        assertDailyState(baseline.store(), expected);
        assertSnapshot(later, (laterEnd - laterStart + 1) * 2000L, laterEnd - laterStart + 1);
        assertSnapshot(first, (firstEnd - firstStart + 1) * 1000L, firstEnd - firstStart + 1);
        assertThat(databaseState(other.store())).isEqualTo(otherBefore);
    }

    @Test void concurrentWorkerCannotPassEarlierAnalysisAndOtherStoreCanFinish() throws Exception {
        var first = fixture(SalesOverlapWorkbook.daily(1, 10, 1000));
        var later = enqueue(first, SalesOverlapWorkbook.daily(5, 15, 2000));
        var other = fixture(SalesOverlapWorkbook.daily(1, 2, 777));
        var claim = coordinator.claimNext("first-worker").orElseThrow();
        assertThat(claim.uploadId()).isEqualTo(first.receipt().uploadId());
        var analyzing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            analyzing.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("analysis release timeout");
            return invocation.callRealMethod();
        }).when(analysis).analyze(eq(first.store()), anyString(), any(), any());
        var pool = Executors.newSingleThreadExecutor();
        var running = pool.submit(() -> processor.process(claim, () -> true));
        try {
            assertThat(analyzing.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(job(first).getCheckpoint()).isEqualTo(SalesUploadOutboxEntity.Checkpoint.DATA_APPLIED);
            completeNext(other.receipt());
            assertThat(coordinator.claimNext("cannot-pass-first")).isEmpty();
            assertThat(uploads.findById(later.uploadId()).orElseThrow().getStatus()).isEqualTo(SalesUploadStatus.PENDING);
            assertDailyState(first.store(), days(first.receipt(), 1, 10, 1000));
        } finally {
            release.countDown();
            try {
                assertThat(running.get(20, TimeUnit.SECONDS)).isEqualTo("DONE");
            } finally {
                pool.shutdownNow();
            }
        }
        completeNext(later);
        var expected = days(first.receipt(), 1, 10, 1000);
        replaceExpected(expected, later, 5, 15, 2000);
        assertDailyState(first.store(), expected);
        assertSnapshot(first.receipt(), 10000, 10);
        assertSnapshot(later, 22000, 11);
    }

    @Test void retryAfterDataAppliedBlocksLaterUploadThenPreservesLaterFinalCoverage() throws Exception {
        var first = fixture(SalesOverlapWorkbook.daily(1, 10, 1000));
        var later = enqueue(first, SalesOverlapWorkbook.daily(5, 15, 2000));
        doThrow(new TransientDataAccessResourceException("temporary analysis failure"))
                .when(analysis).analyze(eq(first.store()), anyString(), any(), any());
        assertThat(processor.process(coordinator.claimNext("first").orElseThrow(), () -> true)).isEqualTo("READY");
        assertThat(job(first).getCheckpoint()).isEqualTo(SalesUploadOutboxEntity.Checkpoint.DATA_APPLIED);
        assertThat(coordinator.claimNext("later-is-blocked")).isEmpty();
        assertDailyState(first.store(), days(first.receipt(), 1, 10, 1000));
        doCallRealMethod().when(analysis).analyze(eq(first.store()), anyString(), any(), any());
        due(job(first).getId());
        completeNext(first.receipt());
        verify(persistence, times(1)).replaceCoverage(eq(first.receipt().uploadId()), any(), any());
        completeNext(later);
        var expected = days(first.receipt(), 1, 10, 1000);
        replaceExpected(expected, later, 5, 15, 2000);
        assertDailyState(first.store(), expected);
        assertSnapshot(first.receipt(), 10000, 10);
        assertSnapshot(later, 22000, 11);
    }

    @Test void leaseTakeoverResumesBeforeLaterUploadAndOldWorkerCannotOverwriteIt() throws Exception {
        var first = fixture(SalesOverlapWorkbook.daily(1, 10, 1000));
        var later = enqueue(first, SalesOverlapWorkbook.daily(5, 15, 2000));
        var old = coordinator.claimNext("old").orElseThrow();
        var healthy = new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            healthy.set(false);
            return result;
        }).when(analysis).analyze(eq(first.store()), anyString(), any(), any());
        assertThat(processor.process(old, healthy::get)).isEqualTo("OWNERSHIP_LOST");
        assertThat(job(first).getCheckpoint()).isEqualTo(SalesUploadOutboxEntity.Checkpoint.DATA_APPLIED);
        expire(old);
        doCallRealMethod().when(analysis).analyze(eq(first.store()), anyString(), any(), any());
        var replacement = coordinator.claimNext("replacement").orElseThrow();
        assertThat(replacement.uploadId()).isEqualTo(first.receipt().uploadId());
        assertThat(replacement.token()).isNotEqualTo(old.token());
        assertThat(processor.process(replacement, () -> true)).isEqualTo("DONE");
        completeNext(later);
        var beforeStaleWrite = databaseState(first.store());
        var oldData = parser.parse(new java.io.ByteArrayInputStream(SalesOverlapWorkbook.daily(1, 10, 1000)));
        assertThatThrownBy(() -> coordinator.write(old, job -> {
            persistence.replaceCoverage(old.uploadId(), old.storeId(), oldData);
            return null;
        })).isInstanceOf(SalesOutboxOwnershipLostException.class);
        assertThat(processor.process(old, () -> true)).isEqualTo("OWNERSHIP_LOST");
        assertThat(databaseState(first.store())).isEqualTo(beforeStaleWrite);
        verify(persistence, times(1)).replaceCoverage(eq(first.receipt().uploadId()), any(), any());
        var expected = days(first.receipt(), 1, 10, 1000);
        replaceExpected(expected, later, 5, 15, 2000);
        assertDailyState(first.store(), expected);
        assertSnapshot(first.receipt(), 10000, 10);
        assertSnapshot(later, 22000, 11);
    }

    @Test void terminalFailureAfterDataAppliedAllowsLaterUploadWithoutPretendingFullRollback() throws Exception {
        var first = fixture(SalesOverlapWorkbook.daily(1, 10, 1000));
        var later = enqueue(first, SalesOverlapWorkbook.daily(5, 15, 2000));
        doThrow(new IllegalArgumentException("terminal analysis failure"))
                .when(analysis).analyze(eq(first.store()), anyString(), any(), any());
        assertThat(processor.process(coordinator.claimNext("first").orElseThrow(), () -> true)).isEqualTo("DEAD");
        assertThat(job(first).getCheckpoint()).isEqualTo(SalesUploadOutboxEntity.Checkpoint.DATA_APPLIED);
        assertThat(uploads.findById(first.receipt().uploadId()).orElseThrow().getStatus()).isEqualTo(SalesUploadStatus.FAILED);
        assertThat(runs.findById(first.receipt().analysisRunId()).orElseThrow().getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_analyses WHERE analysis_run_id=?",
                Integer.class, first.receipt().analysisRunId())).isZero();
        assertDailyState(first.store(), days(first.receipt(), 1, 10, 1000));
        doCallRealMethod().when(analysis).analyze(eq(first.store()), anyString(), any(), any());
        completeNext(later);
        var expected = days(first.receipt(), 1, 10, 1000);
        replaceExpected(expected, later, 5, 15, 2000);
        assertDailyState(first.store(), expected);
        assertSnapshot(later, 22000, 11);
    }

    @Test void replacementRollbackRestoresExistingOrdersItemsSummariesAndCheckpoint() throws Exception {
        var baseline = fixture(SalesOverlapWorkbook.daily(1, 18, 500));
        completeNext(baseline.receipt());
        var later = enqueue(baseline, SalesOverlapWorkbook.daily(5, 15, 2000));
        var before = databaseState(baseline.store());
        var claim = coordinator.claimNext("later").orElseThrow();
        var data = parser.parse(new java.io.ByteArrayInputStream(SalesOverlapWorkbook.daily(5, 15, 2000)));
        assertThatThrownBy(() -> coordinator.write(claim, job -> {
            var result = persistence.replaceCoverage(job.getUploadId(), job.getStoreId(), data);
            job.dataApplied(result.appliedRecordCount(), clock.now());
            jobs.flush();
            throw new TransientDataAccessResourceException("fail after replacement flush before commit");
        })).isInstanceOf(TransientDataAccessResourceException.class);
        assertThat(databaseState(baseline.store())).isEqualTo(before);
        assertThat(jobs.findById(claim.id()).orElseThrow().getCheckpoint())
                .isEqualTo(SalesUploadOutboxEntity.Checkpoint.ACCEPTED);
        assertSnapshot(baseline.receipt(), 9000, 18);
        assertThat(processor.process(claim, () -> true)).isEqualTo("DONE");
        var expected = days(baseline.receipt(), 1, 18, 500);
        replaceExpected(expected, later, 5, 15, 2000);
        assertDailyState(baseline.store(), expected);
    }

    @Test void missingDayInsideLaterCoverageRemovesOldOrdersWithoutClaimingConfirmedZero() throws Exception {
        var first = fixture(SalesOverlapWorkbook.daily(1, 7, 1000));
        completeNext(first.receipt());
        var later = enqueue(first, SalesOverlapWorkbook.coverage(3, 5, java.util.Map.of(3, 2000L, 5, 2000L)));
        completeNext(later);
        var expected = days(first.receipt(), 1, 7, 1000);
        expected.remove(4);
        replaceExpected(expected, later, 3, 3, 2000);
        replaceExpected(expected, later, 5, 5, 2000);
        assertOrders(first.store(), expected);
        var missing = jdbc.queryForMap("SELECT * FROM sales_daily_summaries WHERE store_id=? AND sales_date=?",
                first.store(), SalesOverlapWorkbook.date(4));
        assertThat(((Number) missing.get("total_net_amount")).longValue()).isZero();
        assertThat(((Number) missing.get("order_count")).intValue()).isZero();
        assertThat(missing.get("day_status")).isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_daily_summaries WHERE store_id=?",
                Integer.class, first.store())).isEqualTo(7);
        assertSnapshot(later, 4000, 2);
        assertSnapshot(first.receipt(), 7000, 7);
    }

    @Test void coverageIncludesLastMicrosecondButPreservesNextMidnightAndOutsideRowIds() throws Exception {
        var first = fixture(SalesOverlapWorkbook.daily(4, 6, 1000));
        completeNext(first.receipt());
        var originalIds = jdbc.queryForList("SELECT id FROM sales_orders WHERE store_id=? ORDER BY ordered_at", Long.class, first.store());
        long replacedId = originalIds.get(1);
        setOrderTime(replacedId, SalesOverlapWorkbook.date(5).atTime(23, 59, 59, 999999000));
        setOrderTime(originalIds.get(2), SalesOverlapWorkbook.date(6).atStartOfDay());
        var outside = jdbc.queryForList("SELECT id,ordered_at FROM sales_orders WHERE store_id=? AND id<>? ORDER BY id",
                first.store(), replacedId);
        var later = enqueue(first, SalesOverlapWorkbook.daily(5, 5, 2000));
        completeNext(later);
        var expected = days(first.receipt(), 4, 6, 1000);
        replaceExpected(expected, later, 5, 5, 2000);
        assertDailyState(first.store(), expected);
        assertThat(jdbc.queryForList("SELECT id,ordered_at FROM sales_orders WHERE store_id=? AND sales_upload_id=? ORDER BY id",
                first.store(), first.receipt().uploadId())).isEqualTo(outside);
        assertSnapshot(later, 2000, 1);
    }

    private SalesUploadReceipt enqueue(Fixture fixture, byte[] content) {
        return receipt.upload(new SalesUploadCommand(fixture.store(), fixture.user(), "overlap.xlsx", content));
    }

    private void setOrderTime(long id, java.time.LocalDateTime time) {
        // Match the application's Timestamp binding rather than mixing UTC SQL literals with JVM-local values.
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("UPDATE sales_orders SET ordered_at=? WHERE id=?");
            statement.setTimestamp(1, java.sql.Timestamp.valueOf(time));
            statement.setLong(2, id);
            return statement;
        });
    }

    private void completeNext(SalesUploadReceipt expected) {
        var claim = coordinator.claimNext("overlap-worker").orElseThrow();
        assertThat(claim.uploadId()).isEqualTo(expected.uploadId());
        assertThat(processor.process(claim, () -> true)).isEqualTo("DONE");
        assertThat(jobs.findById(claim.id()).orElseThrow().getStatus()).isEqualTo(SalesUploadOutboxEntity.Status.DONE);
        assertThat(uploads.findById(expected.uploadId()).orElseThrow().getStatus()).isEqualTo(SalesUploadStatus.COMPLETED);
        assertThat(runs.findById(expected.analysisRunId()).orElseThrow().getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
    }

    private java.util.SortedMap<Integer, ExpectedDay> days(SalesUploadReceipt source, int start, int end, long amount) {
        var result = new java.util.TreeMap<Integer, ExpectedDay>();
        replaceExpected(result, source, start, end, amount);
        return result;
    }

    private void replaceExpected(java.util.Map<Integer, ExpectedDay> expected, SalesUploadReceipt source,
                                 int start, int end, long amount) {
        for (int day = start; day <= end; day++) {
            expected.put(day, new ExpectedDay(SalesOverlapWorkbook.date(day), source.uploadId(), amount));
        }
    }

    private void assertOrders(long store, java.util.SortedMap<Integer, ExpectedDay> expected) {
        var actual = jdbc.query("""
                SELECT o.ordered_at,o.sales_upload_id,i.net_amount
                FROM sales_orders o JOIN sales_order_items i ON i.sales_order_id=o.id
                WHERE o.store_id=? ORDER BY o.ordered_at,o.id,i.id
                """, (rs, row) -> new ExpectedDay(rs.getTimestamp("ordered_at").toLocalDateTime().toLocalDate(),
                        rs.getLong("sales_upload_id"), rs.getLong("net_amount")), store);
        assertThat(actual).containsExactlyElementsOf(expected.values());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_orders WHERE store_id=?", Long.class, store))
                .isEqualTo((long) expected.size());
    }

    private void assertDailyState(long store, java.util.SortedMap<Integer, ExpectedDay> expected) {
        assertOrders(store, expected);
        var actual = jdbc.query("""
                SELECT sales_date,total_net_amount,menu_net_amount,order_count,menu_quantity,day_status
                FROM sales_daily_summaries WHERE store_id=? ORDER BY sales_date
                """, (rs, row) -> new ExpectedSummary(rs.getDate("sales_date").toLocalDate(),
                        rs.getLong("total_net_amount"), rs.getLong("menu_net_amount"),
                        rs.getInt("order_count"), rs.getInt("menu_quantity"), rs.getString("day_status")), store);
        assertThat(actual).containsExactlyElementsOf(expected.values().stream()
                .map(day -> new ExpectedSummary(day.date(), day.amount(), day.amount(), 1, 1, "COMPLETE")).toList());
    }

    private void assertSnapshot(SalesUploadReceipt source, long total, int orderCount) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sales_analyses WHERE analysis_run_id=?",
                Integer.class, source.analysisRunId())).isEqualTo(1);
        for (var metric : java.util.Map.of("TOTAL_SALES", total, "ORDER_COUNT", (long) orderCount).entrySet()) {
            var value = jdbc.queryForObject("""
                    SELECT m.metric_value FROM analysis_metrics m
                    JOIN sales_analyses a ON a.id=m.sales_analysis_id
                    WHERE a.analysis_run_id=? AND m.metric_code=?
                    """, java.math.BigDecimal.class, source.analysisRunId(), metric.getKey());
            assertThat(value).isEqualByComparingTo(java.math.BigDecimal.valueOf(metric.getValue()));
        }
    }

    private java.util.List<java.util.List<java.util.Map<String, Object>>> databaseState(long store) {
        return java.util.List.of(
                jdbc.queryForList("SELECT * FROM sales_orders WHERE store_id=? ORDER BY id", store),
                jdbc.queryForList("SELECT i.* FROM sales_order_items i JOIN sales_orders o ON o.id=i.sales_order_id WHERE o.store_id=? ORDER BY i.id", store),
                jdbc.queryForList("SELECT * FROM sales_daily_summaries WHERE store_id=? ORDER BY sales_date", store));
    }

    private record ExpectedDay(java.time.LocalDate date, long uploadId, long amount) {}
    private record ExpectedSummary(java.time.LocalDate date, long total, long menu, int orders, int quantity, String status) {}

    private Fixture fixture() throws Exception {
        return fixture(sample());
    }

    private Fixture fixture(byte[] content) throws Exception {
        long user = IDS.incrementAndGet(), store = IDS.incrementAndGet();
        jdbc.update("INSERT INTO users(id,email,password_hash,phone,created_at,updated_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                user, user + "@outbox.example", "test-only", String.format("010%08d", user));
        jdbc.update("INSERT INTO stores(id,owner_user_id,business_registration_no,name,postal_code,address,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                store, user, String.format("%010d", store), "outbox test", "01234", "test", "ACTIVE");
        return new Fixture(user, store, receipt.upload(new SalesUploadCommand(store, user, "sales.xlsx", content)));
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
