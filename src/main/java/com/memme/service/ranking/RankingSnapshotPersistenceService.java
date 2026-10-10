package com.memme.service.ranking;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.memme.entity.ranking.RankingEntryEntity;
import com.memme.entity.ranking.RankingProfileEntity;
import com.memme.entity.ranking.RankingSnapshotEntity;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.AnalysisRunStatus;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.repository.ranking.RankingEntryRepository;
import com.memme.repository.ranking.RankingProfileRepository;
import com.memme.repository.ranking.RankingSnapshotRepository;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.sales.upload.SalesDailyStatusResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RankingSnapshotPersistenceService {

    private static final int MAX_NICKNAME_ATTEMPTS = 100;

    private final RankingSnapshotRepository snapshotRepository;
    private final RankingEntryRepository entryRepository;
    private final RankingProfileRepository profileRepository;
    private final StoreRepository storeRepository;
    private final SalesDailySummaryRepository dailySummaryRepository;
    private final StoreBusinessHoursRepository businessHoursRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final SalesDailyStatusResolver dailyStatusResolver;
    private final RankingSalesPeriodAggregator periodAggregator;
    private final RankingGrowthCalculator growthCalculator;

    public RankingSnapshotPersistenceService(
            RankingSnapshotRepository snapshotRepository,
            RankingEntryRepository entryRepository,
            RankingProfileRepository profileRepository,
            StoreRepository storeRepository,
            SalesDailySummaryRepository dailySummaryRepository,
            StoreBusinessHoursRepository businessHoursRepository,
            AnalysisRunRepository analysisRunRepository,
            SalesDailyStatusResolver dailyStatusResolver,
            RankingSalesPeriodAggregator periodAggregator,
            RankingGrowthCalculator growthCalculator
    ) {
        this.snapshotRepository = snapshotRepository;
        this.entryRepository = entryRepository;
        this.profileRepository = profileRepository;
        this.storeRepository = storeRepository;
        this.dailySummaryRepository = dailySummaryRepository;
        this.businessHoursRepository = businessHoursRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.dailyStatusResolver = dailyStatusResolver;
        this.periodAggregator = periodAggregator;
        this.growthCalculator = growthCalculator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void calculate(RankingPeriodCalculator.PeriodPair period) {
        RankingSnapshotEntity snapshot = snapshotRepository.saveAndFlush(RankingSnapshotEntity.create(
                period.periodStart(),
                period.periodEnd(),
                period.comparisonStart(),
                period.comparisonEnd(),
                nextRevision(period)
        ));
        snapshot.start();

        Map<Long, List<RankingSalesPeriodAggregator.AnalysisPeriod>> completedAnalysisPeriodsByStore =
                completedAnalysisPeriodsByStore();
        List<RankingGrowthCalculator.StoreSales> eligibleStores = storeRepository.findAllActiveStoreOwners().stream()
                .map(store -> salesForStore(store.getStoreId(), period, completedAnalysisPeriodsByStore))
                .flatMap(java.util.Optional::stream)
                .toList();

        Set<String> usedNicknames = new HashSet<>(profileRepository.findAll().stream()
                .map(RankingProfileEntity::getAnonymousNickname)
                .toList());
        List<RankingEntryEntity> entries = growthCalculator.rank(eligibleStores).stream()
                .map(rankedStore -> createEntry(snapshot.getId(), rankedStore, usedNicknames))
                .toList();
        entryRepository.saveAll(entries);
        snapshot.complete();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(RankingPeriodCalculator.PeriodPair period) {
        RankingSnapshotEntity failedSnapshot = RankingSnapshotEntity.create(
                period.periodStart(),
                period.periodEnd(),
                period.comparisonStart(),
                period.comparisonEnd(),
                nextRevision(period)
        );
        failedSnapshot.start();
        failedSnapshot.fail();
        snapshotRepository.saveAndFlush(failedSnapshot);
    }

    private int nextRevision(RankingPeriodCalculator.PeriodPair period) {
        return snapshotRepository
                .findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndOrderByRevisionNoDesc(
                        period.periodStart(),
                        period.periodEnd(),
                        period.comparisonStart(),
                        period.comparisonEnd()
                )
                .map(snapshot -> Math.incrementExact(snapshot.getRevisionNo()))
                .orElse(1);
    }

    private Map<Long, List<RankingSalesPeriodAggregator.AnalysisPeriod>> completedAnalysisPeriodsByStore() {
        return analysisRunRepository.findAll().stream()
                .filter(run -> run.getStatus() == AnalysisRunStatus.COMPLETED)
                .filter(run -> run.getPeriodStart() != null && run.getPeriodEnd() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        AnalysisRunEntity::getStoreId,
                        java.util.stream.Collectors.mapping(
                                run -> new RankingSalesPeriodAggregator.AnalysisPeriod(
                                        run.getPeriodStart(),
                                        run.getPeriodEnd()
                                ),
                                java.util.stream.Collectors.toList()
                        )
                ));
    }

    private java.util.Optional<RankingGrowthCalculator.StoreSales> salesForStore(
            Long storeId,
            RankingPeriodCalculator.PeriodPair period,
            Map<Long, List<RankingSalesPeriodAggregator.AnalysisPeriod>> completedAnalysisPeriodsByStore
    ) {
        Map<LocalDate, RankingSalesPeriodAggregator.DailySales> dailySalesByDate = new HashMap<>();
        for (SalesDailySummaryEntity summary : dailySummaryRepository
                .findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                        storeId,
                        period.comparisonStart(),
                        period.periodEnd()
                )) {
            dailySalesByDate.put(summary.getSalesDate(), new RankingSalesPeriodAggregator.DailySales(
                    summary.getDayStatus(),
                    summary.getTotalNetAmount()
            ));
        }

        Set<LocalDate> confirmedClosedDates = confirmedClosedDates(storeId, period);
        List<RankingSalesPeriodAggregator.AnalysisPeriod> completedAnalysisPeriods =
                completedAnalysisPeriodsByStore.getOrDefault(storeId, List.of());
        java.util.OptionalLong selectedSales = periodAggregator.aggregate(
                period.periodStart(),
                period.periodEnd(),
                dailySalesByDate,
                confirmedClosedDates,
                completedAnalysisPeriods
        );
        java.util.OptionalLong comparisonSales = periodAggregator.aggregate(
                period.comparisonStart(),
                period.comparisonEnd(),
                dailySalesByDate,
                confirmedClosedDates,
                completedAnalysisPeriods
        );
        if (selectedSales.isEmpty() || comparisonSales.isEmpty() || comparisonSales.getAsLong() == 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new RankingGrowthCalculator.StoreSales(
                storeId,
                selectedSales.getAsLong(),
                comparisonSales.getAsLong()
        ));
    }

    private Set<LocalDate> confirmedClosedDates(
            Long storeId,
            RankingPeriodCalculator.PeriodPair period
    ) {
        Map<Integer, StoreBusinessHours> hoursByDayOfWeek = new HashMap<>();
        for (StoreBusinessHours businessHours : businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(storeId)) {
            hoursByDayOfWeek.put(businessHours.getDayOfWeek(), businessHours);
        }

        Set<LocalDate> closedDates = new HashSet<>();
        for (LocalDate date = period.comparisonStart(); !date.isAfter(period.periodEnd()); date = date.plusDays(1)) {
            StoreBusinessHours businessHours = hoursByDayOfWeek.get(date.getDayOfWeek().getValue());
            if (businessHours != null && Boolean.TRUE.equals(dailyStatusResolver.confirmedClosedStatus(
                    date,
                    businessHours.getUpdatedAt(),
                    businessHours.isClosed()
            ))) {
                closedDates.add(date);
            }
        }
        return closedDates;
    }

    private RankingEntryEntity createEntry(
            Long snapshotId,
            RankingGrowthCalculator.RankedStore rankedStore,
            Set<String> usedNicknames
    ) {
        RankingProfileEntity profile = profileRepository.findByStoreId(rankedStore.storeId())
                .orElseGet(() -> createProfile(rankedStore.storeId(), usedNicknames));
        return RankingEntryEntity.create(
                snapshotId,
                profile.getId(),
                rankedStore.rankNo(),
                rankedStore.growthRate(),
                rankedStore.selectedPeriodSales(),
                rankedStore.comparisonPeriodSales()
        );
    }

    private RankingProfileEntity createProfile(Long storeId, Set<String> usedNicknames) {
        for (int attempt = 0; attempt < MAX_NICKNAME_ATTEMPTS; attempt++) {
            String nickname = "사장님 " + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
            if (usedNicknames.add(nickname)) {
                return profileRepository.saveAndFlush(RankingProfileEntity.create(storeId, nickname));
            }
        }
        throw new IllegalStateException("익명 랭킹 닉네임을 생성하지 못했습니다.");
    }
}
