package com.memme.service.sales.forecast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesForecastEntity;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesForecastRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesExpectedForecastQueryServiceTest {
    private final SalesDailySummaryRepository dailySummaryRepository = mock(SalesDailySummaryRepository.class);
    private final SalesForecastRepository forecastRepository = mock(SalesForecastRepository.class);
    private final StoreOwnershipRepository ownershipRepository = mock(StoreOwnershipRepository.class);
    private final SalesExpectedForecastQueryService service = new SalesExpectedForecastQueryService(
            dailySummaryRepository, forecastRepository, ownershipRepository
    );

    @Test
    void returnsSeptemberForecastWhenLatestActualSalesDateIsAugustEnd() {
        when(ownershipRepository.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(301L)).thenReturn(List.of(
                summary(LocalDate.of(2026, 8, 1), 100),
                summary(LocalDate.of(2026, 8, 31), 200)
        ));
        when(forecastRepository.findAllByStoreIdAndTargetDateBetweenOrderByTargetDateAsc(
                301L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        )).thenReturn(List.of(
                forecast(LocalDate.of(2026, 9, 1), 400, 350, 450),
                forecast(LocalDate.of(2026, 9, 2), 600, 500, 700)
        ));

        var result = service.query(7L, 301L).orElseThrow();

        assertThat(result.targetMonth().toString()).isEqualTo("2026-09");
        assertThat(result.actualSalesAmount()).isEqualByComparingTo("0");
        assertThat(result.forecastSalesAmount()).isEqualByComparingTo("1000");
        assertThat(result.expectedSalesAmount()).isEqualByComparingTo("1000");
        assertThat(result.lowerBound()).isEqualByComparingTo("850");
        assertThat(result.upperBound()).isEqualByComparingTo("1150");
    }

    @Test
    void combinesCurrentMonthActualSalesWithRemainingDailyForecasts() {
        when(ownershipRepository.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(301L)).thenReturn(List.of(
                summary(LocalDate.of(2026, 8, 31), 100),
                summary(LocalDate.of(2026, 9, 1), 200),
                summary(LocalDate.of(2026, 9, 20), 300)
        ));
        when(forecastRepository.findAllByStoreIdAndTargetDateBetweenOrderByTargetDateAsc(
                301L, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 30)
        )).thenReturn(List.of(
                forecast(LocalDate.of(2026, 9, 21), 400, 350, 450),
                forecast(LocalDate.of(2026, 9, 22), 600, 500, 700)
        ));

        var result = service.query(7L, 301L).orElseThrow();

        assertThat(result.targetMonth().toString()).isEqualTo("2026-09");
        assertThat(result.actualSalesAmount()).isEqualByComparingTo("500");
        assertThat(result.forecastSalesAmount()).isEqualByComparingTo("1000");
        assertThat(result.expectedSalesAmount()).isEqualByComparingTo("1500");
        assertThat(result.lowerBound()).isEqualByComparingTo("1350");
        assertThat(result.upperBound()).isEqualByComparingTo("1650");
    }

    @Test
    void returnsEmptyWhenForecastHasNotBeenGenerated() {
        when(ownershipRepository.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(301L)).thenReturn(List.of(
                summary(LocalDate.of(2026, 8, 31), 100)
        ));
        when(forecastRepository.findAllByStoreIdAndTargetDateBetweenOrderByTargetDateAsc(
                301L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)
        )).thenReturn(List.of());

        assertThat(service.query(7L, 301L)).isEmpty();
    }

    @Test
    void rejectsStoreNotOwnedBySessionUser() {
        assertThatThrownBy(() -> service.query(7L, 999L))
                .isInstanceOfSatisfying(SalesAnalysisRequestException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(SalesAnalysisRequestException.Reason.STORE_OWNER_REQUIRED));
        verifyNoInteractions(dailySummaryRepository, forecastRepository);
    }

    private SalesDailySummaryEntity summary(LocalDate salesDate, long amount) {
        return SalesDailySummaryEntity.create(301L, salesDate, amount, amount, 1, 1);
    }

    private SalesForecastEntity forecast(LocalDate targetDate, long predicted, long lower, long upper) {
        return SalesForecastEntity.create(
                301L, 88L, targetDate, LocalDate.of(2026, 8, 31),
                predicted, lower, upper, "v1", LocalDateTime.of(2026, 8, 31, 10, 0)
        );
    }
}
