package com.memme.service.ranking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import com.memme.entity.ranking.RankingSnapshotEntity;
import com.memme.repository.ranking.RankingEntryRepository;
import com.memme.repository.ranking.RankingProfileRepository;
import com.memme.repository.ranking.RankingSnapshotRepository;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.sales.upload.SalesDailyStatusResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RankingSnapshotPersistenceLockModeTest {

    private final RankingSnapshotRepository snapshotRepository = mock(RankingSnapshotRepository.class);
    private final RankingRecalculationLockService mysqlLockService = mock(RankingRecalculationLockService.class);
    private RankingRecalculationLockProperties properties;
    private RankingSnapshotPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        when(snapshotRepository.findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndOrderByRevisionNoDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(snapshotRepository.saveAndFlush(any(RankingSnapshotEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void keepsTheMysqlPeriodLockInMysqlMode() {
        properties = new RankingRecalculationLockProperties();
        properties.setLockProvider(RankingRecalculationLockProperties.Provider.MYSQL);
        createService();

        persistenceService.calculate(period());

        verify(mysqlLockService).acquire(period());
    }

    @Test
    void skipsTheMysqlPeriodLockInRedisMode() {
        properties = new RankingRecalculationLockProperties();
        properties.setLockProvider(RankingRecalculationLockProperties.Provider.REDIS);
        createService();

        persistenceService.calculate(period());

        verify(mysqlLockService, never()).acquire(any());
    }

    @Test
    void keepsTheMysqlPeriodLockForFailedHistoryInMysqlMode() {
        properties = new RankingRecalculationLockProperties();
        properties.setLockProvider(RankingRecalculationLockProperties.Provider.MYSQL);
        createService();

        persistenceService.recordFailed(period());

        verify(mysqlLockService).acquire(period());
    }

    @Test
    void skipsTheMysqlPeriodLockForFailedHistoryInRedisMode() {
        properties = new RankingRecalculationLockProperties();
        properties.setLockProvider(RankingRecalculationLockProperties.Provider.REDIS);
        createService();

        persistenceService.recordFailed(period());

        verify(mysqlLockService, never()).acquire(any());
    }

    private void createService() {
        persistenceService = new RankingSnapshotPersistenceService(
                snapshotRepository,
                mysqlLockService,
                mock(RankingEntryRepository.class),
                mock(RankingProfileRepository.class),
                mock(StoreRepository.class),
                mock(SalesDailySummaryRepository.class),
                mock(StoreBusinessHoursRepository.class),
                mock(AnalysisRunRepository.class),
                mock(SalesDailyStatusResolver.class),
                mock(RankingSalesPeriodAggregator.class),
                mock(RankingGrowthCalculator.class),
                properties
        );
    }

    private RankingPeriodCalculator.PeriodPair period() {
        return new RankingPeriodCalculator.PeriodPair(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31)
        );
    }
}
