package com.memme.service.sales.outbox;

import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.RejectedExecutionException;
import com.memme.entity.sales.SalesUploadOutboxClaim;
import com.memme.service.sales.upload.SalesUploadWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class SalesUploadOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(SalesUploadOutboxDispatcher.class);
    private final SalesUploadOutboxCoordinator coordinator;
    private final SalesUploadWorker worker;
    private final ThreadPoolTaskExecutor executor;
    private final SalesOutboxTelemetry telemetry;
    private final boolean enabled;
    private final boolean dispatchEnabled;
    private final Semaphore slot = new Semaphore(1);
    private final String owner = UUID.randomUUID().toString();

    public SalesUploadOutboxDispatcher(SalesUploadOutboxCoordinator coordinator, SalesUploadWorker worker,
            @Qualifier("salesUploadExecutor") ThreadPoolTaskExecutor executor, SalesOutboxTelemetry telemetry,
            @Value("${app.sales.outbox.enabled:false}") boolean enabled,
            @Value("${app.sales.outbox.dispatch-enabled:true}") boolean dispatchEnabled) {
        this.coordinator = coordinator; this.worker = worker; this.executor = executor;
        this.telemetry = telemetry; this.enabled = enabled; this.dispatchEnabled = dispatchEnabled;
    }

    @Scheduled(fixedDelayString = "${app.sales.outbox.poll-interval-ms:1000}")
    public void poll() {
        if (!enabled || !dispatchEnabled || !slot.tryAcquire()) return;
        boolean submitted = false;
        SalesUploadOutboxClaim claim = null;
        try {
            if (executor.getActiveCount() > 0 || !executor.getThreadPoolExecutor().getQueue().isEmpty()) return;
            var found = coordinator.claimNext(owner);
            if (found.isEmpty()) return;
            claim = found.get();
            worker.processOutbox(claim, slot::release);
            submitted = true;
        } catch (RejectedExecutionException rejected) {
            telemetry.record("REJECTED", 0);
            if (claim != null) {
                try { coordinator.rejected(claim); }
                catch (RuntimeException failure) { log.warn("Outbox rejected registration will recover by lease expiry", failure); }
            }
        } catch (RuntimeException failure) {
            log.warn("Outbox dispatch unavailable; durable records remain for recovery", failure);
        } finally {
            if (!submitted) slot.release();
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void reportOutcomes() {
        if (enabled) log.info("sales_outbox_outcomes={}", telemetry.snapshot());
    }
}
