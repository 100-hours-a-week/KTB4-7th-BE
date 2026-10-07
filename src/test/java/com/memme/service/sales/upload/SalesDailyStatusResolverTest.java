package com.memme.service.sales.upload;

import com.memme.entity.sales.SalesDailyStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalesDailyStatusResolverTest {

    private final SalesDailyStatusResolver resolver = new SalesDailyStatusResolver();

    @Test
    void orderRowsMakeTheDateCompleteEvenWhenTheRecurringScheduleSaysClosed() {
        assertThat(resolver.resolve(true, false, true)).isEqualTo(SalesDailyStatus.COMPLETE);
    }

    @Test
    void aConfiguredClosedDayWithoutOrderRowsIsClosed() {
        assertThat(resolver.resolve(false, false, true)).isEqualTo(SalesDailyStatus.CLOSED);
    }

    @Test
    void aConfirmedZeroSalesDayIsCompleteWhenTheStoreIsOpen() {
        assertThat(resolver.resolve(false, true, false)).isEqualTo(SalesDailyStatus.COMPLETE);
    }

    @Test
    void anOpenDayWithoutSalesEvidenceIsMissingRatherThanZeroSales() {
        assertThat(resolver.resolve(false, false, false)).isEqualTo(SalesDailyStatus.MISSING);
    }

    @Test
    void aDayWithoutScheduleOrSalesEvidenceRemainsUnknown() {
        assertThat(resolver.resolve(false, false, null)).isEqualTo(SalesDailyStatus.UNKNOWN);
    }

    @Test
    void aHistoricalDateBeforeTheLatestScheduleEditHasNoReliableClosureEvidence() {
        assertThat(resolver.confirmedClosedStatus(
                LocalDate.of(2026, 10, 6),
                LocalDateTime.of(2026, 10, 7, 10, 0),
                true
        )).isNull();
    }

    @Test
    void aDateAfterTheLatestScheduleEditUsesTheSavedWeeklySchedule() {
        assertThat(resolver.confirmedClosedStatus(
                LocalDate.of(2026, 10, 8),
                LocalDateTime.of(2026, 10, 7, 10, 0),
                true
        )).isTrue();
    }
}
