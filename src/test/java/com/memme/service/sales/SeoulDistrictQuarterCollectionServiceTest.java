package com.memme.service.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.memme.dto.solution.external.SeoulEstimatedSalesResponse;
import com.memme.dto.solution.external.SeoulFootTrafficResponse;
import com.memme.dto.solution.external.SeoulStoreCountResponse;
import com.memme.service.solution.external.SeoulDistrictApiService;
import com.memme.service.sales.insight.SalesInsightRegenerationService;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OutputCaptureExtension.class)
class SeoulDistrictQuarterCollectionServiceTest {

    private final SeoulDistrictApiService api = mock(SeoulDistrictApiService.class);
    private final SeoulDistrictQuarterAggregationService aggregation = mock(SeoulDistrictQuarterAggregationService.class);
    private final SeoulDistrictQuarterImportPersistenceService persistence =
            mock(SeoulDistrictQuarterImportPersistenceService.class);
    private final SalesInsightRegenerationService regeneration = mock(SalesInsightRegenerationService.class);
    private final SeoulDistrictQuarterCollectionService service =
            new SeoulDistrictQuarterCollectionService(api, aggregation, persistence, regeneration);

    @Test
    void 완료된_분기는_API를_다시_조회하지_않는다() {
        when(persistence.begin("2026Q2")).thenReturn(Optional.empty());

        assertThat(service.collect("2026Q2")).isFalse();

        verifyNoInteractions(api, aggregation);
    }

    @Test
    void 세_자료를_끝까지_조회한_뒤_한번에_저장한다(CapturedOutput output) {
        when(persistence.begin("2026Q2")).thenReturn(Optional.of(7L));
        var sales = new SeoulEstimatedSalesResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 12_000_000L, 100L, 1L, 1L, 1L, 1L, 1L, 1L);
        var stores = new SeoulStoreCountResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 4L, 0L, 0L);
        var traffic = new SeoulFootTrafficResponse.Row("20262", "1168051000", "역삼1동",
                100L, 1L, 1L, 1L, 1L, 1L, 1L);
        when(api.estimatedSales(anyInt(), anyInt(), eq("20262"))).thenAnswer(invocation -> {
            int start = invocation.getArgument(0);
            return Optional.of(new SeoulEstimatedSalesResponse(
                    new SeoulEstimatedSalesResponse.Data(1001, null,
                            start == 1 ? Collections.nCopies(1000, sales) : List.of(sales)), null));
        });
        when(api.storeCount(1, 1000, "20262")).thenReturn(Optional.of(new SeoulStoreCountResponse(
                new SeoulStoreCountResponse.Data(1, null, List.of(stores)), null)));
        when(api.footTraffic(1, 1000)).thenReturn(Optional.of(new SeoulFootTrafficResponse(
                new SeoulFootTrafficResponse.Data(1, null, List.of(traffic)), null)));
        var benchmark = new SeoulDistrictQuarterAggregationService.Benchmark(
                "1168051000", 4, 3_000_000L, "14-17", "14-17");
        when(aggregation.aggregate(eq("2026Q2"), anyList(), anyList(), anyList())).thenReturn(List.of(benchmark));

        assertThat(service.collect("2026Q2")).isTrue();

        verify(api).estimatedSales(1, 1000, "20262");
        verify(api).estimatedSales(1001, 1001, "20262");
        verify(persistence).complete(7L, List.of(benchmark));
        verify(persistence, never()).fail(anyLong());
        verify(regeneration).regenerateAfterDistrictQuarterImport("2026Q2");
        assertThat(output).contains("quarter=2026Q2, benchmarkRows=1");
    }

    @Test
    void 상권_인사이트_재생성_실패가_완료된_분기_적재를_실패로_되돌리지_않는다() {
        when(persistence.begin("2026Q2")).thenReturn(Optional.of(7L));
        var sales = new SeoulEstimatedSalesResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 12_000_000L, 100L, 1L, 1L, 1L, 1L, 1L, 1L);
        var stores = new SeoulStoreCountResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 4L, 0L, 0L);
        var traffic = new SeoulFootTrafficResponse.Row("20262", "1168051000", "역삼1동",
                100L, 1L, 1L, 1L, 1L, 1L, 1L);
        when(api.estimatedSales(1, 1000, "20262")).thenReturn(Optional.of(new SeoulEstimatedSalesResponse(
                new SeoulEstimatedSalesResponse.Data(1, null, List.of(sales)), null)));
        when(api.storeCount(1, 1000, "20262")).thenReturn(Optional.of(new SeoulStoreCountResponse(
                new SeoulStoreCountResponse.Data(1, null, List.of(stores)), null)));
        when(api.footTraffic(1, 1000)).thenReturn(Optional.of(new SeoulFootTrafficResponse(
                new SeoulFootTrafficResponse.Data(1, null, List.of(traffic)), null)));
        var benchmark = new SeoulDistrictQuarterAggregationService.Benchmark(
                "1168051000", 4, 3_000_000L, "14-17", "14-17");
        when(aggregation.aggregate(eq("2026Q2"), anyList(), anyList(), anyList())).thenReturn(List.of(benchmark));
        org.mockito.Mockito.doThrow(new RuntimeException("AI unavailable"))
                .when(regeneration).regenerateAfterDistrictQuarterImport("2026Q2");

        assertThat(service.collect("2026Q2")).isTrue();

        verify(persistence).complete(7L, List.of(benchmark));
        verify(persistence, never()).fail(anyLong());
    }

    @Test
    void 외부_조회_실패는_실패_상태로_남겨_재시도할_수_있다() {
        when(persistence.begin("2026Q2")).thenReturn(Optional.of(7L));
        when(api.estimatedSales(1, 1000, "20262")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.collect("2026Q2")).isInstanceOf(IllegalStateException.class);

        verify(persistence).fail(7L);
        verify(persistence, never()).complete(anyLong(), anyList());
        verify(regeneration, never()).regenerateAfterDistrictQuarterImport(anyString());
    }

    @Test
    void 일부_페이지만_오면_완료하지_않는다() {
        when(persistence.begin("2026Q2")).thenReturn(Optional.of(7L));
        when(api.estimatedSales(1, 1000, "20262")).thenReturn(Optional.of(new SeoulEstimatedSalesResponse(
                new SeoulEstimatedSalesResponse.Data(1001, null, List.of(new SeoulEstimatedSalesResponse.Row(
                        "20262", "1168051000", "역삼1동", "CS100010", "커피-음료", 1L, 1L,
                        1L, 1L, 1L, 1L, 1L, 1L))), null)));

        assertThatThrownBy(() -> service.collect("2026Q2")).isInstanceOf(IllegalStateException.class);

        verify(persistence).fail(7L);
        verify(persistence, never()).complete(anyLong(), anyList());
        verify(regeneration, never()).regenerateAfterDistrictQuarterImport(anyString());
    }

    @Test
    void 집계할_수_있는_행정동이_없으면_완료하지_않는다() {
        when(persistence.begin("2026Q2")).thenReturn(Optional.of(7L));
        var sales = new SeoulEstimatedSalesResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 12_000_000L, 100L, 1L, 1L, 1L, 1L, 1L, 1L);
        var stores = new SeoulStoreCountResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 4L, 0L, 0L);
        var traffic = new SeoulFootTrafficResponse.Row("20262", "1168051000", "역삼1동",
                100L, 1L, 1L, 1L, 1L, 1L, 1L);
        when(api.estimatedSales(1, 1000, "20262")).thenReturn(Optional.of(new SeoulEstimatedSalesResponse(
                new SeoulEstimatedSalesResponse.Data(1, null, List.of(sales)), null)));
        when(api.storeCount(1, 1000, "20262")).thenReturn(Optional.of(new SeoulStoreCountResponse(
                new SeoulStoreCountResponse.Data(1, null, List.of(stores)), null)));
        when(api.footTraffic(1, 1000)).thenReturn(Optional.of(new SeoulFootTrafficResponse(
                new SeoulFootTrafficResponse.Data(1, null, List.of(traffic)), null)));

        when(aggregation.aggregate(eq("2026Q2"), anyList(), anyList(), anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> service.collect("2026Q2")).isInstanceOf(IllegalStateException.class);

        verify(persistence).fail(7L);
        verify(persistence, never()).complete(anyLong(), anyList());
    }
}
