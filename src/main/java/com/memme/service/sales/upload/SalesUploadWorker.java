package com.memme.service.sales.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.memme.entity.sales.SalesUploadOutboxClaim;
import com.memme.service.sales.outbox.*;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SalesUploadWorker {

    private static final Logger log = LoggerFactory.getLogger(SalesUploadWorker.class);
    private final SalesUploadProcessingService processingService;
    private final SalesUploadOutboxProcessor outboxProcessor;
    private final SalesUploadOutboxCoordinator coordinator;
    private final SalesOutboxTelemetry telemetry;
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("sales-outbox-heartbeat").factory());

    public SalesUploadWorker(SalesUploadProcessingService processingService,
            SalesUploadOutboxProcessor outboxProcessor, SalesUploadOutboxCoordinator coordinator,
            SalesOutboxTelemetry telemetry) {
        this.processingService = processingService;
        this.outboxProcessor = outboxProcessor; this.coordinator = coordinator; this.telemetry = telemetry;
    }

    @Async("salesUploadExecutor")
    public void process(Long uploadId) {
        try {
            processingService.process(uploadId);
        } catch (RuntimeException exception) {
            log.error("Sales upload processing failed for uploadId={}", uploadId, exception);
        }
    }

    @Async("salesUploadExecutor")
    public void processOutbox(SalesUploadOutboxClaim claim, Runnable finished) {
        var healthy = new AtomicBoolean(true);
        ScheduledFuture<?> heartbeat = null;
        long started = System.nanoTime();
        try {
            heartbeat = heartbeats.scheduleAtFixedRate(() -> {
                if (!healthy.get()) return;
                try { coordinator.heartbeat(claim); }
                catch (RuntimeException failure) { healthy.set(false); }
            }, 10, 10, TimeUnit.SECONDS);
            telemetry.record(outboxProcessor.process(claim, healthy::get), System.nanoTime() - started);
        } finally {
            if (heartbeat != null) heartbeat.cancel(false);
            finished.run();
        }
    }

    @PreDestroy
    void stopHeartbeats() { heartbeats.shutdownNow(); }
}
