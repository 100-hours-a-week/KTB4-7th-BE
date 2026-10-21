package com.memme.service.sales.insight;

import com.memme.config.AiCallConcurrencyGate;
import com.memme.dto.sales.SalesInsightMetrics;
import com.memme.dto.sales.SalesInsightRequest;
import com.memme.dto.sales.SalesInsightStatus;
import com.memme.dto.sales.SalesInsightTriggerType;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.entity.sales.SalesAiInsightEntity;
import com.memme.repository.sales.SalesAiInsightRepository;
import com.memme.repository.sales.SalesAnalysisRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SalesInsightRegenerationService {
    private static final Logger log = LoggerFactory.getLogger(SalesInsightRegenerationService.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int REGENERATION_PAGE_SIZE = 100;
    private final SalesAiInsightRepository insights;
    private final SalesAnalysisRepository analyses;
    private final SalesInsightMetricsAssembler assembler;
    private final DistrictBenchmarkService benchmarks;
    private final SalesInsightClient client;
    private final SalesInsightRegenerationPersistenceService persistence;
    private final SalesInsightRegenerationSourceFingerprint fingerprints;
    private final AiCallConcurrencyGate aiCallConcurrencyGate;
    private final Clock clock;

    public SalesInsightRegenerationService(SalesAiInsightRepository insights, SalesAnalysisRepository analyses,
            SalesInsightMetricsAssembler assembler, DistrictBenchmarkService benchmarks, SalesInsightClient client,
            SalesInsightRegenerationPersistenceService persistence, SalesInsightRegenerationSourceFingerprint fingerprints,
            AiCallConcurrencyGate aiCallConcurrencyGate, Clock clock) {
        this.insights = insights;
        this.analyses = analyses;
        this.assembler = assembler;
        this.benchmarks = benchmarks;
        this.client = client;
        this.persistence = persistence;
        this.fingerprints = fingerprints;
        this.aiCallConcurrencyGate = aiCallConcurrencyGate;
        this.clock = clock;
    }

    public SalesInsightRegenerationOutcome regenerate(Long storeId, YearMonth month) {
        var existing = insights.findByStoreIdAndTargetMonth(storeId, month).orElse(null);
        if (existing == null || existing.getStatus() != SalesAiInsightStatus.COMPLETED) {
            return SalesInsightRegenerationOutcome.SKIPPED;
        }
        Long analysisId = existing.getSalesAnalysisId();
        var analysis = analyses.findById(analysisId).orElse(null);
        if (analysis == null) return SalesInsightRegenerationOutcome.SKIPPED;
        var source = source(storeId, month, analysisId);
        if (source == null) return SalesInsightRegenerationOutcome.SKIPPED;
        var reservation = persistence.reserve(storeId, month, analysisId, source.fingerprint(), now());
        if (reservation.outcome() != SalesInsightRegenerationOutcome.RESERVED) return reservation.outcome();
        try {
            var request = new SalesInsightRequest(storeId, analysisId, analysis.getAnalysisRunId(), month,
                    SalesInsightTriggerType.RETRY, source.metrics(), 3);
            String idempotencyKey = "sales-insight-regeneration:" + reservation.executionId()
                    + ":" + reservation.attempt();
            var response = aiCallConcurrencyGate.call(() -> client.generate(request, idempotencyKey));
            if (response == null || response.status() != SalesInsightStatus.COMPLETED
                    || response.data() == null || !month.equals(response.data().targetMonth())) {
                throw new IllegalStateException("AI insight candidate incomplete");
            }
            var current = source(storeId, month, analysisId);
            if (current == null || !source.fingerprint().equals(current.fingerprint())) {
                throw new IllegalStateException("insight source changed during generation");
            }
            persistence.complete(storeId, month, analysisId, analysisId, reservation,
                    response.data().insights(), now());
            return SalesInsightRegenerationOutcome.COMPLETED;
        } catch (RuntimeException exception) {
            log.warn("Insight regeneration failed for store {} month {}: {}",
                    storeId, month, exception.getClass().getSimpleName());
            persistence.fail(reservation, now());
            return SalesInsightRegenerationOutcome.FAILED;
        }
    }

    public SalesInsightRegenerationBatchResult regenerateAfterDistrictQuarterImport(String basisQuarter) {
        if (basisQuarter == null || !basisQuarter.matches("[0-9]{4}Q[1-4]")) {
            throw new IllegalArgumentException("basisQuarter must be YYYYQ1..YYYYQ4");
        }
        int year = Integer.parseInt(basisQuarter.substring(0, 4));
        int quarter = Character.digit(basisQuarter.charAt(5), 10);
        YearMonth startMonth = YearMonth.of(year, (quarter - 1) * 3 + 1);
        YearMonth endMonth = startMonth.plusMonths(8);
        BatchCounts counts = regeneratePages(startMonth, endMonth);
        SalesInsightRegenerationBatchResult result = counts.result();
        log.info("District-triggered sales insight regeneration completed: quarter={}, candidates={}, eligible={}, completed={}, skipped={}, failed={}",
                basisQuarter, result.candidates(), result.eligible(), result.completed(), result.skipped(), result.failed());
        return result;
    }

    private BatchCounts regeneratePages(YearMonth startMonth, YearMonth endMonth) {
        BatchCounts counts = new BatchCounts();
        for (int pageNumber = 0; ; pageNumber++) {
            Page<SalesAiInsightEntity> page = insights.findAllByTargetMonthBetweenAndStatusOrderByIdAsc(
                    startMonth.atDay(1), endMonth.atDay(1), SalesAiInsightStatus.COMPLETED,
                    PageRequest.of(pageNumber, REGENERATION_PAGE_SIZE));
            for (SalesAiInsightEntity insight : page.getContent()) {
                process(insight, counts);
            }
            if (!page.hasNext()) return counts;
        }
    }

    private void process(SalesAiInsightEntity insight, BatchCounts counts) {
        counts.candidates++;
        try {
            Long storeId = insight.getStoreId();
            YearMonth month = insight.getTargetMonth();
            SalesInsightRegenerationOutcome outcome = regenerateWithRetry(storeId, month);
            switch (outcome) {
                case COMPLETED -> {
                    counts.eligible++;
                    counts.completed++;
                }
                case FAILED -> {
                    counts.eligible++;
                    counts.failed++;
                }
                default -> counts.skipped++;
            }
            log.info("Sales insight regeneration result: storeId={}, targetMonth={}, outcome={}",
                    storeId, month, outcome);
        } catch (RuntimeException exception) {
            counts.failed++;
            log.warn("Sales insight regeneration target failed: storeId={}, targetMonth={}, cause={}",
                    insight.getStoreId(), insight.getTargetMonth(), exception.getClass().getSimpleName());
        }
    }

    private SalesInsightRegenerationOutcome regenerateWithRetry(Long storeId, YearMonth month) {
        boolean failed = false;
        for (int attempt = 1; attempt <= SalesInsightRegenerationPersistenceService.MAX_ATTEMPTS; attempt++) {
            SalesInsightRegenerationOutcome outcome = regenerate(storeId, month);
            if (outcome == SalesInsightRegenerationOutcome.FAILED) {
                failed = true;
                if (attempt < SalesInsightRegenerationPersistenceService.MAX_ATTEMPTS) {
                    log.info("Retrying sales insight regeneration: storeId={}, targetMonth={}, attempt={}",
                            storeId, month, attempt + 1);
                }
                continue;
            }
            if (failed && outcome == SalesInsightRegenerationOutcome.RETRY_LIMIT_REACHED) {
                return SalesInsightRegenerationOutcome.FAILED;
            }
            return outcome;
        }
        return SalesInsightRegenerationOutcome.FAILED;
    }

    private Source source(Long storeId, YearMonth month, Long analysisId) {
        var assembled = assembler.assemble(storeId, month);
        if (!assembled.sufficientData() || !assembled.districtBenchmarkEligible()) return null;
        var benchmark = benchmarks.findForInsight(storeId, month).orElse(null);
        if (benchmark == null) return null;
        var base = assembled.metrics();
        var metrics = new SalesInsightMetrics(base.salesSummary(), base.salesTrend(), base.weekdaySales(),
                base.hourlySales(), base.categorySales(), base.menuRankings(), benchmark);
        return new Source(metrics, fingerprints.of(analysisId, month, metrics));
    }

    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(KOREA)); }
    private record Source(SalesInsightMetrics metrics, String fingerprint) {}

    private static final class BatchCounts {
        private long candidates;
        private long eligible;
        private long completed;
        private long skipped;
        private long failed;

        private SalesInsightRegenerationBatchResult result() {
            return new SalesInsightRegenerationBatchResult(candidates, eligible, completed, skipped, failed);
        }
    }
}
