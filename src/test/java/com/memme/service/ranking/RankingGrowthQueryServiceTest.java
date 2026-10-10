package com.memme.service.ranking;

import com.memme.dto.ranking.RankingGrowthResponse;
import com.memme.entity.ranking.RankingEntryEntity;
import com.memme.entity.ranking.RankingProfileEntity;
import com.memme.entity.ranking.RankingSnapshotEntity;
import com.memme.entity.ranking.RankingSnapshotStatus;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.AnalysisRunStatus;
import com.memme.entity.sales.SalesDailyStatus;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.repository.ranking.RankingEntryRepository;
import com.memme.repository.ranking.RankingProfileRepository;
import com.memme.repository.ranking.RankingSnapshotRepository;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.service.sales.upload.SalesDailyStatusResolver;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingGrowthQueryServiceTest {

    private final RankingSnapshotRepository snapshots = mock(RankingSnapshotRepository.class);
    private final RankingEntryRepository entries = mock(RankingEntryRepository.class);
    private final RankingProfileRepository profiles = mock(RankingProfileRepository.class);
    private final StoreOwnershipRepository ownership = mock(StoreOwnershipRepository.class);
    private final SalesDailySummaryRepository dailySummaries = mock(SalesDailySummaryRepository.class);
    private final StoreBusinessHoursRepository businessHours = mock(StoreBusinessHoursRepository.class);
    private final AnalysisRunRepository analysisRuns = mock(AnalysisRunRepository.class);
    private RankingGrowthQueryService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T03:00:00Z"), ZoneOffset.UTC);
        service = new RankingGrowthQueryService(snapshots, entries, profiles, ownership,
                dailySummaries, businessHours, analysisRuns, new SalesDailyStatusResolver(),
                new RankingSalesPeriodAggregator(), clock);
        when(ownership.existsActiveStoreOwnedBy(10L, 1L)).thenReturn(true);
    }

    @Test
    void latestCompletedRevisionWinsOverNewerFailedRevisionAndHidesOtherStoreIdentity() {
        RankingSnapshotEntity completed = snapshot(1, RankingSnapshotStatus.COMPLETED);
        when(snapshots.findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndOrderByRevisionNoDesc(
                any(), any(), any(), any()))
                .thenReturn(Optional.of(snapshot(2, RankingSnapshotStatus.FAILED)));
        when(snapshots.findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndAndStatusOrderByRevisionNoDesc(
                any(), any(), any(), any(), any())).thenReturn(Optional.of(completed));
        RankingProfileEntity mine = profile(100, 10, "사장님 100");
        RankingProfileEntity other = profile(200, 20, "사장님 200");
        when(profiles.findByStoreId(10L)).thenReturn(Optional.of(mine));
        when(profiles.findAllById(any())).thenReturn(List.of(mine, other));
        when(entries.findAllByRankingSnapshotIdOrderByRankNoAsc(1L)).thenReturn(List.of(
                entry(1, 200, 1, "30.0000"), entry(1, 100, 2, "20.0000")));

        RankingGrowthResponse response = service.query(1L, 10L);

        assertThat(response.status()).isEqualTo(RankingGrowthResponse.Status.COMPLETED);
        assertThat(response.data().period().startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.data().period().comparisonStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(response.data().rankings()).extracting(RankingGrowthResponse.Ranking::displayName)
                .containsExactly("사장님 200", "내 매장");
        assertThat(response.data().rankings().getFirst().growthRate()).isEqualByComparingTo("30.0");
        assertThat(response.data().myRanking().rank()).isEqualTo(2);
        assertThat(response.data().myEligibility().status())
                .isEqualTo(RankingGrowthResponse.EligibilityStatus.ELIGIBLE);
    }

    @Test
    void ownRankingRemainsAvailableOutsideTopTwenty() {
        RankingSnapshotEntity completed = snapshot(1, RankingSnapshotStatus.COMPLETED);
        when(snapshots.findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndAndStatusOrderByRevisionNoDesc(
                any(), any(), any(), any(), any())).thenReturn(Optional.of(completed));
        RankingProfileEntity mine = profile(100, 10, "사장님 100");
        List<RankingEntryEntity> ranked = new ArrayList<>();
        List<RankingProfileEntity> rankedProfiles = new ArrayList<>();
        rankedProfiles.add(mine);
        for (int rank = 1; rank <= 20; rank++) {
            ranked.add(entry(1, rank + 100, rank, "25.0000"));
            rankedProfiles.add(profile(rank + 100, rank + 100, "사장님 " + rank));
        }
        ranked.add(entry(1, 100, 21, "10.0000"));
        when(profiles.findByStoreId(10L)).thenReturn(Optional.of(mine));
        when(profiles.findAllById(any())).thenReturn(rankedProfiles);
        when(entries.findAllByRankingSnapshotIdOrderByRankNoAsc(1L)).thenReturn(ranked);

        RankingGrowthResponse response = service.query(1L, 10L);

        assertThat(response.data().rankings()).hasSize(20);
        assertThat(response.data().rankings()).noneMatch(RankingGrowthResponse.Ranking::isMine);
        assertThat(response.data().myRanking().rank()).isEqualTo(21);
        assertThat(response.data().myRanking().includedInTop20()).isFalse();
    }

    @Test
    void noCompletedRevisionReportsLatestProcessingStateWithoutRankings() {
        when(snapshots.findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndOrderByRevisionNoDesc(
                any(), any(), any(), any())).thenReturn(Optional.of(snapshot(2, RankingSnapshotStatus.PROCESSING)));

        RankingGrowthResponse response = service.query(1L, 10L);

        assertThat(response.status()).isEqualTo(RankingGrowthResponse.Status.PROCESSING);
        assertThat(response.data().rankings()).isEmpty();
        assertThat(response.data().period().calculatedAt()).isNull();
        assertThat(response.data().myEligibility().status())
                .isEqualTo(RankingGrowthResponse.EligibilityStatus.UNKNOWN);
    }

    @Test
    void noSnapshotReportsNotCalculatedAndFailedSnapshotReportsFailed() {
        RankingGrowthResponse beforeCalculation = service.query(1L, 10L);
        assertThat(beforeCalculation.status()).isEqualTo(RankingGrowthResponse.Status.NOT_CALCULATED);

        when(snapshots.findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndOrderByRevisionNoDesc(
                any(), any(), any(), any())).thenReturn(Optional.of(snapshot(2, RankingSnapshotStatus.FAILED)));
        RankingGrowthResponse afterFailure = service.query(1L, 10L);
        assertThat(afterFailure.status()).isEqualTo(RankingGrowthResponse.Status.FAILED);
        assertThat(afterFailure.data().rankings()).isEmpty();
    }

    @Test
    void rejectsSessionThatDoesNotOwnTheActiveStore() {
        assertThatThrownBy(() -> service.query(2L, 10L))
                .isInstanceOf(com.memme.exception.sales.SalesAnalysisRequestException.class);
    }

    @Test
    void propagatesUnexpectedRepositoryFailureToCommonHandler() {
        when(snapshots.findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndAndStatusOrderByRevisionNoDesc(
                any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("db unavailable"));

        assertThatThrownBy(() -> service.query(1L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db unavailable");
    }

    @Test
    void unknownDayExcludesOwnStoreAsIncompleteData() {
        completedEmptyRanking();
        when(dailySummaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(any(), any(), any()))
                .thenReturn(dailySales(100L, 100L, LocalDate.of(2026, 9, 15)));
        stubCompletedAnalysis();

        RankingGrowthResponse response = service.query(1L, 10L);

        assertThat(response.data().myRanking()).isNull();
        assertThat(response.data().myEligibility().reason())
                .isEqualTo(RankingGrowthResponse.EligibilityReason.DATA_INCOMPLETE);
    }

    @Test
    void completeSalesWithoutCompletedAnalysisRemainIneligible() {
        completedEmptyRanking();
        when(dailySummaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(any(), any(), any()))
                .thenReturn(dailySales(100L, 100L, null));

        RankingGrowthResponse response = service.query(1L, 10L);

        assertThat(response.data().myEligibility().reason())
                .isEqualTo(RankingGrowthResponse.EligibilityReason.ANALYSIS_NOT_COMPLETED);
    }

    @Test
    void zeroComparisonSalesExcludeOwnStore() {
        completedEmptyRanking();
        when(dailySummaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(any(), any(), any()))
                .thenReturn(dailySales(100L, 0L, null));
        stubCompletedAnalysis();

        RankingGrowthResponse response = service.query(1L, 10L);

        assertThat(response.data().myEligibility().reason())
                .isEqualTo(RankingGrowthResponse.EligibilityReason.COMPARISON_SALES_ZERO);
    }

    @Test
    void confirmedClosureDoesNotOverrideAnExplicitUnknownDay() {
        completedEmptyRanking();
        LocalDate closedDate = LocalDate.of(2026, 9, 15);
        when(dailySummaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(any(), any(), any()))
                .thenReturn(dailySales(100L, 0L, closedDate));
        stubCompletedAnalysis();
        when(businessHours.findAllByStoreIdOrderByDayOfWeekAsc(10L)).thenReturn(List.of(
                StoreBusinessHours.create(mock(Store.class), closedDate.getDayOfWeek().getValue(),
                        null, null, true, LocalDateTime.of(2026, 7, 1, 0, 0))));

        RankingGrowthResponse response = service.query(1L, 10L);

        assertThat(response.data().myEligibility().reason())
                .isEqualTo(RankingGrowthResponse.EligibilityReason.DATA_INCOMPLETE);
    }

    @Test
    void eligibilityDoesNotReadAllStoresAnalysisHistory() {
        completedEmptyRanking();
        when(dailySummaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(any(), any(), any()))
                .thenReturn(dailySales(100L, 100L, null));
        when(analysisRuns.findAll()).thenThrow(new IllegalStateException("unbounded analysis read"));

        RankingGrowthResponse response = service.query(1L, 10L);

        assertThat(response.data().myEligibility().reason())
                .isEqualTo(RankingGrowthResponse.EligibilityReason.ANALYSIS_NOT_COMPLETED);
        verify(analysisRuns).findAllOverlappingPeriod(10L, AnalysisRunStatus.COMPLETED,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));
    }

    private void completedEmptyRanking() {
        when(snapshots.findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndAndStatusOrderByRevisionNoDesc(
                any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(snapshot(1, RankingSnapshotStatus.COMPLETED)));
    }

    private void stubCompletedAnalysis() {
        when(analysisRuns.findAllOverlappingPeriod(10L, AnalysisRunStatus.COMPLETED,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(completedAnalysis()));
    }

    private List<SalesDailySummaryEntity> dailySales(long selectedAmount, long comparisonAmount, LocalDate unknown) {
        List<SalesDailySummaryEntity> days = new ArrayList<>();
        for (LocalDate date = LocalDate.of(2026, 8, 1); !date.isAfter(LocalDate.of(2026, 9, 30));
                date = date.plusDays(1)) {
            days.add(SalesDailySummaryEntity.create(10L, date,
                    date.getMonthValue() == 9 ? selectedAmount : comparisonAmount,
                    0, 1, 1, date.equals(unknown) ? SalesDailyStatus.UNKNOWN : SalesDailyStatus.COMPLETE));
        }
        return days;
    }

    private AnalysisRunEntity completedAnalysis() {
        AnalysisRunEntity run = AnalysisRunEntity.pending(10L, 1L, 500L);
        run.start(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));
        run.complete();
        return run;
    }

    private RankingSnapshotEntity snapshot(long id, RankingSnapshotStatus status) {
        RankingSnapshotEntity snapshot = RankingSnapshotEntity.create(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 1);
        ReflectionTestUtils.setField(snapshot, "id", id);
        if (status != RankingSnapshotStatus.PENDING) snapshot.start();
        if (status == RankingSnapshotStatus.COMPLETED) snapshot.complete();
        if (status == RankingSnapshotStatus.FAILED) snapshot.fail();
        return snapshot;
    }

    private RankingProfileEntity profile(long id, long storeId, String name) {
        RankingProfileEntity profile = RankingProfileEntity.create(storeId, name);
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }

    private RankingEntryEntity entry(long snapshotId, long profileId, int rank, String growth) {
        return RankingEntryEntity.create(snapshotId, profileId, rank,
                new BigDecimal(growth), 120L, 100L);
    }
}
