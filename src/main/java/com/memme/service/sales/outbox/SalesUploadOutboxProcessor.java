package com.memme.service.sales.outbox;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.BooleanSupplier;
import com.memme.entity.sales.*;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.sales.outbox.SalesOutboxDatabaseClock;
import com.memme.service.sales.TossPosWorkbookData;
import com.memme.service.sales.TossPosWorkbookParser;
import com.memme.service.sales.analysis.SalesAnalysisService;
import com.memme.service.sales.analysis.SalesAnalysisSnapshotService;
import com.memme.service.sales.storage.SalesFileStorage;
import com.memme.service.sales.upload.SalesUploadPersistenceService;
import com.memme.service.sales.upload.SalesUploadProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SalesUploadOutboxProcessor {
    private static final Logger log = LoggerFactory.getLogger(SalesUploadOutboxProcessor.class);
    private final SalesUploadOutboxCoordinator coordinator;
    private final SalesUploadRepository uploads;
    private final AnalysisRunRepository runs;
    private final SalesOutboxDatabaseClock clock;
    private final SalesFileStorage files;
    private final TossPosWorkbookParser parser;
    private final SalesUploadPersistenceService persistence;
    private final SalesAnalysisService analysis;
    private final SalesAnalysisSnapshotService snapshots;
    private final SalesUploadProcessingService postProcessing;

    public SalesUploadOutboxProcessor(SalesUploadOutboxCoordinator coordinator, SalesUploadRepository uploads,
            AnalysisRunRepository runs, SalesOutboxDatabaseClock clock, SalesFileStorage files,
            TossPosWorkbookParser parser, SalesUploadPersistenceService persistence,
            SalesAnalysisService analysis, SalesAnalysisSnapshotService snapshots,
            SalesUploadProcessingService postProcessing) {
        this.coordinator = coordinator; this.uploads = uploads; this.runs = runs; this.clock = clock;
        this.files = files; this.parser = parser; this.persistence = persistence;
        this.analysis = analysis; this.snapshots = snapshots; this.postProcessing = postProcessing;
    }

    public String process(SalesUploadOutboxClaim claim, BooleanSupplier healthy) {
        Long analysisId;
        TossPosWorkbookData data;
        try {
            requireHealthy(healthy);
            var input = coordinator.write(claim, job -> {
                var upload = upload(job); var run = run(job);
                SalesUploadOutboxCoordinator.requireBinding(job, upload, run);
                if (upload.getStatus() == SalesUploadStatus.PENDING) upload.startProcessing();
                if (upload.getStatus() != SalesUploadStatus.PROCESSING) throw new IllegalStateException("upload not processing");
                return new Input(upload.getStorageKey(), upload.getFileChecksum());
            });
            byte[] content = files.load(input.storageKey());
            if (!sha256(content).equals(input.checksum())) throw new IllegalArgumentException("원본 파일 체크섬이 일치하지 않습니다.");
            try {
                data = parser.parse(new ByteArrayInputStream(content));
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
            TossPosWorkbookData parsed = data;
            requireHealthy(healthy);
            coordinator.write(claim, job -> {
                if (job.getCheckpoint() == SalesUploadOutboxEntity.Checkpoint.ACCEPTED) {
                    var upload = upload(job); var run = run(job);
                    SalesUploadOutboxCoordinator.requireBinding(job, upload, run);
                    upload.advancePhase(SalesUploadProcessingPhase.NORMALIZING);
                    upload.setCoverage(parsed.periodStart(), parsed.periodEnd(), parsed.items().size());
                    if (run.getStatus() == AnalysisRunStatus.PENDING) run.start(parsed.periodStart(), parsed.periodEnd());
                    if (run.getStatus() != AnalysisRunStatus.PROCESSING) throw new IllegalStateException("analysis not processing");
                    upload.advancePhase(SalesUploadProcessingPhase.AGGREGATING);
                    var result = persistence.replaceCoverage(job.getUploadId(), job.getStoreId(), parsed);
                    job.dataApplied(result.appliedRecordCount(), clock.now());
                    upload.advancePhase(SalesUploadProcessingPhase.ANALYZING);
                }
                return null;
            });
            var result = analysis.analyze(claim.storeId(), "CUSTOM", data.periodStart(), data.periodEnd());
            requireHealthy(healthy);
            analysisId = coordinator.write(claim, job -> {
                var upload = upload(job); var run = run(job);
                SalesUploadOutboxCoordinator.requireBinding(job, upload, run);
                if (job.getCheckpoint() != SalesUploadOutboxEntity.Checkpoint.DATA_APPLIED) throw new IllegalStateException("invalid checkpoint");
                Long id = snapshots.persistForRun(job.getAnalysisRunId(), result);
                upload.complete(job.getAppliedRecordCount()); run.complete(); job.complete(clock.now());
                return id;
            });
        } catch (SalesOutboxOwnershipLostException exception) {
            return "OWNERSHIP_LOST";
        } catch (RuntimeException exception) {
            if (!healthy.getAsBoolean()) return "OWNERSHIP_LOST";
            try {
                return coordinator.failed(claim, exception).name();
            } catch (SalesOutboxOwnershipLostException lost) {
                return "OWNERSHIP_LOST";
            } catch (RuntimeException recordingFailure) {
                log.warn("Outbox failure recording unavailable; lease expiry will allow recovery", recordingFailure);
                return "UNRECORDED";
            }
        }
        // DB 작업은 이미 DONE. 이 후처리는 이번 Outbox의 영속 보장 범위 밖이다.
        try {
            postProcessing.afterOutboxBasicAnalysis(claim.uploadId(), claim.analysisRunId(), analysisId, data);
        } catch (RuntimeException exception) {
            log.warn("Post processing failed after durable basic analysis completion", exception);
        }
        return "DONE";
    }

    private SalesUploadEntity upload(SalesUploadOutboxEntity job) { return uploads.findById(job.getUploadId()).orElseThrow(); }
    private AnalysisRunEntity run(SalesUploadOutboxEntity job) { return runs.findById(job.getAnalysisRunId()).orElseThrow(); }
    private static void requireHealthy(BooleanSupplier healthy) { if (!healthy.getAsBoolean()) throw new SalesOutboxOwnershipLostException(); }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private record Input(String storageKey, String checksum) {}
}
