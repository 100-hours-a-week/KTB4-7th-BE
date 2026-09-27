package com.memme.service.sales.analysis;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesAnalysisQueryServiceTest {
    private final SalesAnalysisService analysis = mock(SalesAnalysisService.class);
    private final SalesDailySummaryRepository dailySummaryRepository = mock(SalesDailySummaryRepository.class);
    private final StoreOwnershipRepository ownership = mock(StoreOwnershipRepository.class);
    // UTC Sunday, Seoul Monday: verifies business timezone and week boundary.
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-30T15:30:00Z"), ZoneId.of("Asia/Seoul"));
    private final SalesAnalysisQueryService service = new SalesAnalysisQueryService(
            analysis, dailySummaryRepository, ownership, clock
    );

    @Test
    void returnsDistinctAvailableMonthsInAscendingOrder() {
        when(ownership.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        when(dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(301L)).thenReturn(List.of(
                SalesDailySummaryEntity.create(301L, LocalDate.of(2026, 3, 1), 0, 0, 0, 0),
                SalesDailySummaryEntity.create(301L, LocalDate.of(2026, 3, 31), 0, 0, 0, 0),
                SalesDailySummaryEntity.create(301L, LocalDate.of(2026, 4, 1), 0, 0, 0, 0)
        ));

        var response = service.getAvailableMonths(7L, 301L);

        assertThat(response.months()).containsExactly("2026-03", "2026-04");
    }

    @Test
    void deniesAvailableMonthsForStoreNotOwnedBySessionUser() {
        assertThatThrownBy(() -> service.getAvailableMonths(7L, 999L))
                .isInstanceOfSatisfying(SalesAnalysisRequestException.class,
                        e -> assertThat(e.getReason())
                                .isEqualTo(SalesAnalysisRequestException.Reason.STORE_OWNER_REQUIRED));
        verifyNoInteractions(dailySummaryRepository);
    }

    @Test
    void resolvesPresetsInSeoulWithMondayWeekStart() {
        var today = LocalDate.of(2026, 8, 31);
        assertThat(service.resolvePeriod("TODAY", null, null).startDate()).isEqualTo(today);
        assertThat(service.resolvePeriod("THIS_WEEK", null, null).startDate()).isEqualTo(today);
        assertThat(service.resolvePeriod("THIS_MONTH", null, null).startDate()).isEqualTo(today.withDayOfMonth(1));
        assertThat(service.resolvePeriod("THIS_MONTH", null, null).endDate()).isEqualTo(today);
    }

    @Test
    void validatesCustomDatesAndRejectsConflictingPresetParameters() {
        assertThat(service.resolvePeriod("CUSTOM", "2024-02-29", "2024-03-01").startDate())
                .isEqualTo(LocalDate.of(2024, 2, 29));
        String[][] invalid = {{"CUSTOM", null, null}, {"CUSTOM", "2026-02-29", "2026-03-01"},
                {"CUSTOM", "2026-09-02", "2026-09-01"}, {"TODAY", "2026-09-01", null},
                {"UNKNOWN", null, null}, {"CUSTOM", "2026-9-1", "2026-09-02"}};
        for (String[] args : invalid) {
            assertThatThrownBy(() -> service.resolvePeriod(args[0], args[1], args[2]))
                    .isInstanceOf(SalesAnalysisRequestException.class);
        }
    }

    @Test
    void onlyQueriesStoreVerifiedAgainstSessionUser() {
        when(ownership.existsActiveStoreOwnedBy(301L, 7L)).thenReturn(true);
        service.query(7L, 301L, "TODAY", null, null);
        verify(analysis).analyze(301L, "TODAY", LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 31));
    }

    @Test
    void deniesStaleOrForgedOwnershipBeforeReadingSales() {
        assertThatThrownBy(() -> service.query(7L, 999L, "TODAY", null, null))
                .isInstanceOfSatisfying(SalesAnalysisRequestException.class,
                        e -> assertThat(e.getReason()).isEqualTo(SalesAnalysisRequestException.Reason.STORE_OWNER_REQUIRED));
        verifyNoInteractions(analysis);
    }
}
