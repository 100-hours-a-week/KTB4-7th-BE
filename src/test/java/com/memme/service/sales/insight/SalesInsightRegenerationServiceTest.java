package com.memme.service.sales.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.config.AiCallConcurrencyGate;
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
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
                client, persistence, fingerprints, new AiCallConcurrencyGate(), clock);
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

    @Test
    void 분기_적재후_분기부터_이후_두_분기까지_완료된_인사이트를_조회한다() {
        var insights = mock(SalesAiInsightRepository.class);
        var emptyPage = new PageImpl<com.memme.entity.sales.SalesAiInsightEntity>(List.of(),
                PageRequest.of(0, 100), 0);
        when(insights.findAllByTargetMonthBetweenAndStatusOrderByIdAsc(
                java.time.LocalDate.of(2026, 4, 1), java.time.LocalDate.of(2026, 12, 1),
                SalesAiInsightStatus.COMPLETED, PageRequest.of(0, 100))).thenReturn(emptyPage);
        var service = new SalesInsightRegenerationService(insights, mock(SalesAnalysisRepository.class),
                mock(SalesInsightMetricsAssembler.class), mock(DistrictBenchmarkService.class),
                mock(SalesInsightClient.class), mock(SalesInsightRegenerationPersistenceService.class),
                mock(SalesInsightRegenerationSourceFingerprint.class), new AiCallConcurrencyGate(),
                Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), ZoneId.of("Asia/Seoul")));

        SalesInsightRegenerationBatchResult result = service.regenerateAfterDistrictQuarterImport("2026Q2");

        assertThat(result.candidates()).isZero();
        verify(insights).findAllByTargetMonthBetweenAndStatusOrderByIdAsc(
                java.time.LocalDate.of(2026, 4, 1), java.time.LocalDate.of(2026, 12, 1),
                SalesAiInsightStatus.COMPLETED, PageRequest.of(0, 100));
    }

    @Test
    void 상권_적재후_500건이_넘어도_모든_인사이트를_페이지별로_처리한다() {
        var insights = mock(SalesAiInsightRepository.class);
        List<SalesAiInsightEntity> rows = IntStream.range(0, 501)
                .mapToObj(index -> {
                    var insight = mock(SalesAiInsightEntity.class);
                    when(insight.getStoreId()).thenReturn((long) index + 1);
                    when(insight.getTargetMonth()).thenReturn(MONTH);
                    return insight;
                })
                .toList();
        when(insights.findAllByTargetMonthBetweenAndStatusOrderByIdAsc(
                eq(java.time.LocalDate.of(2026, 4, 1)), eq(java.time.LocalDate.of(2026, 12, 1)),
                eq(SalesAiInsightStatus.COMPLETED), any()))
                .thenAnswer(invocation -> {
                    PageRequest pageRequest = invocation.getArgument(3);
                    int start = (int) pageRequest.getOffset();
                    int end = Math.min(start + pageRequest.getPageSize(), rows.size());
                    return new PageImpl<>(rows.subList(start, end), pageRequest, rows.size());
                });
        var service = new SalesInsightRegenerationService(insights, mock(SalesAnalysisRepository.class),
                mock(SalesInsightMetricsAssembler.class), mock(DistrictBenchmarkService.class),
                mock(SalesInsightClient.class), mock(SalesInsightRegenerationPersistenceService.class),
                mock(SalesInsightRegenerationSourceFingerprint.class), new AiCallConcurrencyGate(),
                Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        var spy = org.mockito.Mockito.spy(service);
        org.mockito.Mockito.doReturn(SalesInsightRegenerationOutcome.COMPLETED)
                .when(spy).regenerate(any(Long.class), eq(MONTH));

        SalesInsightRegenerationBatchResult result = spy.regenerateAfterDistrictQuarterImport("2026Q2");

        assertThat(result.candidates()).isEqualTo(501);
        assertThat(result.completed()).isEqualTo(501);
        org.mockito.Mockito.verify(spy, org.mockito.Mockito.times(501)).regenerate(any(Long.class), eq(MONTH));
    }

    @Test
    void 인사이트_재생성_실패는_대상별_최대_세_번_자동_재시도한다() {
        var insights = mock(SalesAiInsightRepository.class);
        var insight = mock(SalesAiInsightEntity.class);
        when(insight.getStoreId()).thenReturn(1L);
        when(insight.getTargetMonth()).thenReturn(MONTH);
        when(insights.findAllByTargetMonthBetweenAndStatusOrderByIdAsc(
                eq(java.time.LocalDate.of(2026, 4, 1)), eq(java.time.LocalDate.of(2026, 12, 1)),
                eq(SalesAiInsightStatus.COMPLETED), any()))
                .thenReturn(new PageImpl<>(List.of(insight), PageRequest.of(0, 100), 1));
        var service = new SalesInsightRegenerationService(insights, mock(SalesAnalysisRepository.class),
                mock(SalesInsightMetricsAssembler.class), mock(DistrictBenchmarkService.class),
                mock(SalesInsightClient.class), mock(SalesInsightRegenerationPersistenceService.class),
                mock(SalesInsightRegenerationSourceFingerprint.class), new AiCallConcurrencyGate(),
                Clock.fixed(Instant.parse("2026-10-21T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        var spy = org.mockito.Mockito.spy(service);
        org.mockito.Mockito.doReturn(SalesInsightRegenerationOutcome.FAILED, SalesInsightRegenerationOutcome.FAILED,
                SalesInsightRegenerationOutcome.COMPLETED).when(spy).regenerate(1L, MONTH);

        SalesInsightRegenerationBatchResult result = spy.regenerateAfterDistrictQuarterImport("2026Q2");

        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        org.mockito.Mockito.verify(spy, org.mockito.Mockito.times(3)).regenerate(1L, MONTH);
    }

}
