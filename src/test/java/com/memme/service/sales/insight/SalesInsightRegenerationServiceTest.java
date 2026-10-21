package com.memme.service.sales.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.sales.DistrictBenchmarkMetric;
import com.memme.dto.sales.SalesInsightMetrics;
import com.memme.dto.sales.SalesInsightRequest;
import com.memme.dto.sales.SalesInsightResponse;
import com.memme.dto.sales.SalesInsightResponseData;
import com.memme.dto.sales.SalesInsightStatus;
import com.memme.entity.sales.SalesAiInsightEntity;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.entity.sales.SalesAnalysisEntity;
import com.memme.repository.sales.SalesAiInsightRepository;
import com.memme.repository.sales.SalesAnalysisRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SalesInsightRegenerationServiceTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 9);

    @Test
    void sendsFreshCandidateAndReplacesOnlyAfterCompletedResponse() {
        var insights = mock(SalesAiInsightRepository.class);
        var analyses = mock(SalesAnalysisRepository.class);
        var assembler = mock(SalesInsightMetricsAssembler.class);
        var benchmarks = mock(DistrictBenchmarkService.class);
        var client = mock(SalesInsightClient.class);
        var persistence = mock(SalesInsightRegenerationPersistenceService.class);
        var fingerprints = mock(SalesInsightRegenerationSourceFingerprint.class);
        var clock = Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        var service = new SalesInsightRegenerationService(insights, analyses, assembler, benchmarks,
                client, persistence, fingerprints, clock);
        var current = mock(SalesAiInsightEntity.class);
        var analysis = mock(SalesAnalysisEntity.class);
        var metrics = new SalesInsightMetrics(null, List.of(), List.of(), List.of(), List.of(), List.of());
        var benchmark = new DistrictBenchmarkMetric("2026Q3", 1000, 5, "11-14", "17-21");
        var reservation = SalesInsightRegenerationReservation.acquired(7L, 1, java.time.LocalDateTime.now(clock));
        when(insights.findByStoreIdAndTargetMonth(1L, MONTH)).thenReturn(Optional.of(current));
        when(current.getStatus()).thenReturn(SalesAiInsightStatus.COMPLETED);
        when(current.getSalesAnalysisId()).thenReturn(10L);
        when(analyses.findById(10L)).thenReturn(Optional.of(analysis));
        when(analysis.getAnalysisRunId()).thenReturn(20L);
        when(assembler.assemble(1L, MONTH)).thenReturn(new SalesInsightMetricsAssembler.Result(metrics, true, true));
        when(benchmarks.findForInsight(1L, MONTH)).thenReturn(Optional.of(benchmark));
        when(fingerprints.of(any(), any(), any())).thenReturn("a".repeat(64));
        when(persistence.reserve(eq(1L), eq(MONTH), eq(10L), eq("a".repeat(64)), any()))
                .thenReturn(reservation);
        when(client.generate(any(SalesInsightRequest.class), eq("sales-insight-regeneration:7:1")))
                .thenReturn(new SalesInsightResponse("ok", SalesInsightStatus.COMPLETED,
                        new SalesInsightResponseData(MONTH, List.of("new"), null), null));

        assertThat(service.regenerate(1L, MONTH)).isEqualTo(SalesInsightRegenerationOutcome.COMPLETED);
        verify(persistence).complete(eq(1L), eq(MONTH), eq(10L), eq(10L), eq(reservation),
                eq(List.of("new")), any());
        verify(persistence, never()).fail(eq(reservation), any());
    }
}
