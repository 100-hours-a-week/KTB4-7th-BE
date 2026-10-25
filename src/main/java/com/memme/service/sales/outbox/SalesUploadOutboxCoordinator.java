package com.memme.service.sales.outbox;

import java.util.Optional;
import java.util.function.Function;
import com.memme.entity.sales.*;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.sales.outbox.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SalesUploadOutboxCoordinator {
    private final SalesUploadOutboxRepository jobs;
    private final SalesUploadRepository uploads;
    private final AnalysisRunRepository runs;
    private final SalesOutboxDatabaseClock clock;
    private final SalesOutboxRetryPolicy retries;
    private final TransactionTemplate transactions;

    public SalesUploadOutboxCoordinator(SalesUploadOutboxRepository jobs, SalesUploadRepository uploads,
                                       AnalysisRunRepository runs, SalesOutboxDatabaseClock clock,
                                       SalesOutboxRetryPolicy retries, PlatformTransactionManager manager) {
        this.jobs = jobs; this.uploads = uploads; this.runs = runs; this.clock = clock; this.retries = retries;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Optional<SalesUploadOutboxClaim> claimNext(String owner) {
        for (var candidate : jobs.candidates(clock.now())) {
            var claim = transactions.execute(tx -> {
                if (jobs.tryLockStore(candidate.getStoreId()).isEmpty()) return null;
                var job = jobs.lockJob(candidate.getId()).orElseThrow();
                var now = clock.now();
                if (!job.getId().equals(jobs.firstActiveId(job.getStoreId())) || !job.eligible(now)) return null;
                if (job.getAttemptCount() >= job.getMaxAttempts()) {
                    markDead(job, "UPLOAD_PROCESSING_ERROR", "처리 재시도 횟수를 초과했습니다.", now);
                    return null;
                }
                return job.claim(owner, now);
            });
            if (claim != null) return Optional.of(claim);
        }
        return Optional.empty();
    }

    public <T> T write(SalesUploadOutboxClaim claim, Function<SalesUploadOutboxEntity, T> action) {
        return transactions.execute(tx -> {
            jobs.lockStore(claim.storeId());
            var job = jobs.lockJob(claim.id()).orElseThrow(SalesOutboxOwnershipLostException::new);
            var now = clock.now();
            if (!job.owns(claim, now) || !job.getId().equals(jobs.firstActiveId(job.getStoreId()))) {
                throw new SalesOutboxOwnershipLostException();
            }
            T result = action.apply(job);
            if (job.getStatus() == SalesUploadOutboxEntity.Status.RUNNING) job.heartbeat(clock.now());
            jobs.flush();
            return result;
        });
    }

    public void heartbeat(SalesUploadOutboxClaim claim) { write(claim, job -> null); }
    public void rejected(SalesUploadOutboxClaim claim) { write(claim, job -> { job.rejected(clock.now()); return null; }); }

    public SalesUploadOutboxEntity.Status failed(SalesUploadOutboxClaim claim, Throwable failure) {
        return write(claim, job -> {
            var now = clock.now();
            if (retries.retryable(failure) && job.getAttemptCount() < job.getMaxAttempts()) {
                job.retry("UPLOAD_PROCESSING_ERROR", "일시적인 처리 오류로 재시도 대기 중입니다.",
                        now.plus(retries.delay(job.getAttemptCount())), now);
            } else {
                String code = failure instanceof com.memme.exception.sales.TossPosWorkbookValidationException
                        ? "PLATFORM_SCHEMA_MISMATCH" : "UPLOAD_PROCESSING_ERROR";
                markDead(job, code, "매출 파일 처리에 실패했습니다. 파일 또는 처리 환경을 확인해 주세요.", now);
            }
            return job.getStatus();
        });
    }

    private void markDead(SalesUploadOutboxEntity job, String code, String message, java.time.LocalDateTime now) {
        var upload = uploads.findById(job.getUploadId()).orElseThrow();
        var run = runs.findById(job.getAnalysisRunId()).orElseThrow();
        requireBinding(job, upload, run);
        upload.fail(code, message, null); run.fail(message); job.dead(code, message, now);
    }

    public static void requireBinding(SalesUploadOutboxEntity job, SalesUploadEntity upload, AnalysisRunEntity run) {
        if (!job.getUploadId().equals(upload.getId()) || !job.getStoreId().equals(upload.getStoreId())
                || !job.getAnalysisRunId().equals(run.getId()) || !job.getUploadId().equals(run.getBasedOnUploadId())
                || !job.getStoreId().equals(run.getStoreId())) {
            throw new IllegalStateException("Outbox 접수 기록이 일치하지 않습니다.");
        }
    }
}
