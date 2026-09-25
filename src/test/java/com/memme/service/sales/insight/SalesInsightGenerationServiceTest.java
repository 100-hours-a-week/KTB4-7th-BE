package com.memme.service.sales.insight;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import com.memme.dto.sales.SalesInsightMetrics;
import com.memme.dto.sales.SalesInsightResponse;
import com.memme.dto.sales.SalesInsightResponseData;
import com.memme.dto.sales.SalesInsightStatus;
import com.memme.dto.sales.SalesInsightTriggerType;
import com.memme.dto.sales.SalesSummaryMetric;
import com.memme.entity.sales.SalesAiInsightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesInsightGenerationServiceTest {

    private SalesInsightMetricsAssembler assembler;
    private SalesInsightClient client;
    private SalesAiInsightPersistenceService persistence;
    private SalesInsightGenerationService service;

    @BeforeEach
    void setUp() {
        assembler = mock(SalesInsightMetricsAssembler.class);
        client = mock(SalesInsightClient.class);
        persistence = mock(SalesAiInsightPersistenceService.class);
        service = new SalesInsightGenerationService(assembler, client, persistence);
        when(persistence.start(1L, 56L, YearMonth.of(2026, 9))).thenReturn(77L);
    }

    @Test
    void skipsAiCallWhenMonthlySalesDaysAreLessThanFourteen() {
        when(assembler.assemble(1L, YearMonth.of(2026, 9)))
                .thenReturn(new SalesInsightMetricsAssembler.Result(metrics(), false));

        SalesAiInsightStatus status = generate();

        assertThat(status).isEqualTo(SalesAiInsightStatus.INSUFFICIENT_DATA);
        verify(persistence).markInsufficientData(77L);
        verify(client, never()).generate(any());
    }

    @Test
    void storesCompletedAiInsights() {
        when(assembler.assemble(1L, YearMonth.of(2026, 9)))
                .thenReturn(new SalesInsightMetricsAssembler.Result(metrics(), true));
        when(client.generate(any())).thenReturn(new SalesInsightResponse(
                "매출 AI 인사이트를 생성했습니다.",
                SalesInsightStatus.COMPLETED,
                new SalesInsightResponseData(
                        YearMonth.of(2026, 9),
                        List.of("9월 총 매출은 7,920,000원입니다."),
                        null
                ),
                null
        ));

        SalesAiInsightStatus status = generate();

        assertThat(status).isEqualTo(SalesAiInsightStatus.COMPLETED);
        verify(persistence).complete(77L, List.of("9월 총 매출은 7,920,000원입니다."));
    }

    @Test
    void storesInsufficientDataReturnedByAi() {
        when(assembler.assemble(1L, YearMonth.of(2026, 9)))
                .thenReturn(new SalesInsightMetricsAssembler.Result(metrics(), true));
        when(client.generate(any())).thenReturn(new SalesInsightResponse(
                "AI 인사이트에 필요한 데이터가 부족합니다.",
                SalesInsightStatus.INSUFFICIENT_DATA,
                new SalesInsightResponseData(null, null, List.of("SALES_HISTORY")),
                null
        ));

        assertThat(generate()).isEqualTo(SalesAiInsightStatus.INSUFFICIENT_DATA);
        verify(persistence).markInsufficientData(77L);
    }

    @Test
    void storesFailureReturnedByAi() {
        when(assembler.assemble(1L, YearMonth.of(2026, 9)))
                .thenReturn(new SalesInsightMetricsAssembler.Result(metrics(), true));
        when(client.generate(any())).thenReturn(new SalesInsightResponse(
                "매출 AI 인사이트 생성에 실패했습니다.",
                SalesInsightStatus.FAILED,
                null,
                null
        ));

        assertThat(generate()).isEqualTo(SalesAiInsightStatus.FAILED);
        verify(persistence).fail(77L);
    }

    @Test
    void invalidCompletedResponseIsStoredAsFailure() {
        when(assembler.assemble(1L, YearMonth.of(2026, 9)))
                .thenReturn(new SalesInsightMetricsAssembler.Result(metrics(), true));
        when(client.generate(any())).thenReturn(new SalesInsightResponse(
                "매출 AI 인사이트를 생성했습니다.",
                SalesInsightStatus.COMPLETED,
                new SalesInsightResponseData(
                        YearMonth.of(2026, 8),
                        List.of("대상 월이 다른 응답입니다."),
                        null
                ),
                null
        ));

        assertThat(generate()).isEqualTo(SalesAiInsightStatus.FAILED);
        verify(persistence).fail(77L);
        verify(persistence, never()).complete(any(), any());
    }

    @Test
    void aiFailureIsStoredWithoutFailingSalesUploadFlow() {
        when(assembler.assemble(1L, YearMonth.of(2026, 9)))
                .thenReturn(new SalesInsightMetricsAssembler.Result(metrics(), true));
        when(client.generate(any())).thenThrow(new SalesInsightClientException("timeout"));

        SalesAiInsightStatus status = generate();

        assertThat(status).isEqualTo(SalesAiInsightStatus.FAILED);
        verify(persistence).fail(77L);
    }

    private SalesAiInsightStatus generate() {
        return service.generate(
                1L,
                56L,
                34L,
                YearMonth.of(2026, 9),
                SalesInsightTriggerType.UPLOAD
        );
    }

    private SalesInsightMetrics metrics() {
        return new SalesInsightMetrics(
                new SalesSummaryMetric(
                        new BigDecimal("7920000"),
                        new BigDecimal("7480000"),
                        923L,
                        new BigDecimal("8581"),
                        new BigDecimal("0.042")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
