package com.memme.service.sales.analysis;

import static org.assertj.core.api.Assertions.*;

import com.memme.entity.sales.*;
import com.memme.repository.sales.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@org.springframework.test.annotation.DirtiesContext
class SalesAnalysisCacheVersionTest {
    @Autowired SalesAnalysisCacheVersion versions;
    @Autowired SalesDailySummaryRepository days;
    @Autowired SalesOrderRepository orders;
    @Autowired SalesForecastRepository forecasts;
    @Autowired SalesAiInsightRepository insights;
    @Autowired PlatformTransactionManager transactions;
    private final LocalDate date = LocalDate.of(2026, 10, 28);

    @Test
    void committedSummaryOrderForecastAndInsightChangesInvalidateAndRollbackDoesNot() {
        long store = 606_001L;
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> days.saveAndFlush(SalesDailySummaryEntity.create(store, date, 100, 100, 1, 1)));
        String initial = version(tx, store);
        assertThat(version(tx, store)).isEqualTo(initial);
        tx.executeWithoutResult(status -> {
            days.findByStoreIdAndSalesDate(store, date).orElseThrow().update(200, 200, 1, 1);
            days.flush();
            status.setRollbackOnly();
        });
        assertThat(version(tx, store)).isEqualTo(initial);
        tx.executeWithoutResult(status -> {
            days.findByStoreIdAndSalesDate(store, date).orElseThrow().update(200, 200, 1, 1);
            days.flush();
        });
        String changedSummary = version(tx, store);
        assertThat(changedSummary).isNotEqualTo(initial);
        tx.executeWithoutResult(status -> orders.saveAndFlush(SalesOrderEntity.create(606L, store,
                SalesOrderChannel.POS, "replacement", date.atTime(10, 0), true)));
        String changedOrder = version(tx, store);
        assertThat(changedOrder).isNotEqualTo(changedSummary);
        tx.executeWithoutResult(status -> forecasts.saveAndFlush(SalesForecastEntity.create(store, 606L,
                date, date.minusDays(1), 100, 90, 110, "test", date.atTime(9, 0))));
        String changedForecast = version(tx, store);
        assertThat(changedForecast).isNotEqualTo(changedOrder);
        tx.executeWithoutResult(status -> insights.saveAndFlush(SalesAiInsightEntity.pending(store, 606L, YearMonth.from(date))));
        assertThat(version(tx, store)).isNotEqualTo(changedForecast);
        assertThat(version(tx, store + 1)).isNotEqualTo(version(tx, store));
    }

    private String version(TransactionTemplate tx, long store) {
        return tx.execute(status -> versions.read(store, date, date));
    }
}
