package com.memme.service.solution;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesForecastEntity;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesSolutionMetricsAssemblerTest {

    @Test
    void excludesForecastTargetDateSalesFromSolutionMetrics() {
        SalesOrderRepository orderRepository = mock(SalesOrderRepository.class);
        SalesOrderItemRepository itemRepository = mock(SalesOrderItemRepository.class);
        AnalysisRunEntity analysisRun = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(analysisRun.getPeriodStart()).thenReturn(LocalDate.of(2026, 7, 1));
        when(analysisRun.getPeriodEnd()).thenReturn(LocalDate.of(2026, 10, 2));
        when(forecast.getTargetDate()).thenReturn(LocalDate.of(2026, 10, 2));
        when(orderRepository.findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        new SalesSolutionMetricsAssembler(orderRepository, itemRepository)
                .assemble(10L, analysisRun, forecast);

        ArgumentCaptor<LocalDateTime> endExclusive = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository, org.mockito.Mockito.times(2))
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        eq(10L), any(LocalDateTime.class), endExclusive.capture());
        assertThat(endExclusive.getAllValues().getFirst())
                .isEqualTo(LocalDate.of(2026, 10, 2).atStartOfDay());
    }
}
