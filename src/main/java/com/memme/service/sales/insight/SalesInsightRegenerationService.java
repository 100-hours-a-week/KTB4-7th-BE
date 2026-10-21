package com.memme.service.sales.insight;

import com.memme.dto.sales.SalesInsightMetrics;
import com.memme.dto.sales.SalesInsightRequest;
import com.memme.dto.sales.SalesInsightStatus;
import com.memme.dto.sales.SalesInsightTriggerType;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.repository.sales.SalesAiInsightRepository;
import com.memme.repository.sales.SalesAnalysisRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SalesInsightRegenerationService {
    private static final Logger log = LoggerFactory.getLogger(SalesInsightRegenerationService.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final SalesAiInsightRepository insights;
    private final SalesAnalysisRepository analyses;
    private final SalesInsightMetricsAssembler assembler;
    private final DistrictBenchmarkService benchmarks;
    private final SalesInsightClient client;
    private final SalesInsightRegenerationPersistenceService persistence;
    private final SalesInsightRegenerationSourceFingerprint fingerprints;
    private final Clock clock;

    public SalesInsightRegenerationService(SalesAiInsightRepository insights, SalesAnalysisRepository analyses,
            SalesInsightMetricsAssembler assembler, DistrictBenchmarkService benchmarks, SalesInsightClient client,
            SalesInsightRegenerationPersistenceService persistence, SalesInsightRegenerationSourceFingerprint fingerprints,
            Clock clock) {
        this.insights = insights;
        this.analyses = analyses;
        this.assembler = assembler;
        this.benchmarks = benchmarks;
        this.client = client;
        this.persistence = persistence;
        this.fingerprints = fingerprints;
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
            var response = client.generate(request, "sales-insight-regeneration:" + reservation.executionId()
                    + ":" + reservation.attempt());
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
}
