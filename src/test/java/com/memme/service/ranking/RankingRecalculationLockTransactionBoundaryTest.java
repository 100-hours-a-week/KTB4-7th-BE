package com.memme.service.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.memme.repository.ranking.RankingEntryRepository;
import com.memme.repository.ranking.RankingProfileRepository;
import com.memme.repository.ranking.RankingSnapshotRepository;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.sales.upload.SalesDailyStatusResolver;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RankingRecalculationLockTransactionBoundaryTest {

    @Test
    void redisLockEnclosesCalculateRollbackAndFailedSnapshotCommitOutsideDbTransactions() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            Events events = context.getBean(Events.class);
            RankingSnapshotPersistenceService persistence = context.getBean(RankingSnapshotPersistenceService.class);
            assertThat(AopUtils.isAopProxy(persistence)).isTrue();

            RankingRecalculationLockExecutor lockExecutor = new RankingRecalculationLockExecutor() {
                @Override
                public <T> T execute(RankingPeriodCalculator.PeriodPair period, Supplier<T> operation) {
                    events.add(TransactionSynchronizationManager.isActualTransactionActive()
                            ? "lock-acquired-in-db-transaction" : "lock-acquired-outside-db-transaction");
                    try {
                        return operation.get();
                    } finally {
                        events.add(TransactionSynchronizationManager.isActualTransactionActive()
                                ? "lock-released-in-db-transaction" : "lock-released-outside-db-transaction");
                    }
                }
            };
            RankingSnapshotRecalculationService service = new RankingSnapshotRecalculationService(
                    persistence,
                    lockExecutor,
                    Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), ZoneOffset.UTC)
            );

            RankingSnapshotRecalculationService.Result result = service.recalculateCurrentPeriod();

            assertThat(result).isEqualTo(RankingSnapshotRecalculationService.Result.FAILED);
            assertThat(events.values()).containsExactly(
                    "lock-acquired-outside-db-transaction",
                    "calculate-in-db-transaction",
                    "calculate-rolled-back",
                    "record-failed-in-db-transaction",
                    "record-failed-committed",
                    "lock-released-outside-db-transaction"
            );
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {

        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:ranking_lock_tx_boundary;DB_CLOSE_DELAY=-1");
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DriverManagerDataSource dataSource) {
            DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
            transactionManager.setDefaultTimeout(TransactionDefinition.TIMEOUT_DEFAULT);
            return transactionManager;
        }

        @Bean
        Events events() {
            return new Events();
        }

        @Bean
        RankingSnapshotPersistenceService persistenceService(Events events) {
            return new TransactionProbePersistenceService(events);
        }
    }

    static class Events {
        private final List<String> values = new ArrayList<>();

        void add(String value) {
            values.add(value);
        }

        List<String> values() {
            return List.copyOf(values);
        }
    }

    static class TransactionProbePersistenceService extends RankingSnapshotPersistenceService {

        private final Events events;

        TransactionProbePersistenceService(Events events) {
            super(
                    mock(RankingSnapshotRepository.class),
                    mock(RankingRecalculationLockService.class),
                    mock(RankingEntryRepository.class),
                    mock(RankingProfileRepository.class),
                    mock(StoreRepository.class),
                    mock(SalesDailySummaryRepository.class),
                    mock(StoreBusinessHoursRepository.class),
                    mock(AnalysisRunRepository.class),
                    mock(SalesDailyStatusResolver.class),
                    mock(RankingSalesPeriodAggregator.class),
                    mock(RankingGrowthCalculator.class),
                    new RankingRecalculationLockProperties()
            );
            this.events = events;
        }

        @Override
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void calculate(RankingPeriodCalculator.PeriodPair period) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            events.add("calculate-in-db-transaction");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    events.add(status == TransactionSynchronization.STATUS_ROLLED_BACK
                            ? "calculate-rolled-back" : "calculate-not-rolled-back");
                }
            });
            throw new IllegalStateException("simulated calculation failure");
        }

        @Override
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void recordFailed(RankingPeriodCalculator.PeriodPair period) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            events.add("record-failed-in-db-transaction");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    events.add("record-failed-committed");
                }
            });
        }
    }
}
