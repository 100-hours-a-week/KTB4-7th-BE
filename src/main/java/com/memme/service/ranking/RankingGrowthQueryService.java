package com.memme.service.ranking;

import com.memme.dto.ranking.RankingGrowthResponse;
import com.memme.dto.ranking.RankingPeriod;
import com.memme.entity.ranking.RankingEntryEntity;
import com.memme.entity.ranking.RankingProfileEntity;
import com.memme.entity.ranking.RankingSnapshotEntity;
import com.memme.entity.ranking.RankingSnapshotStatus;
import com.memme.entity.sales.AnalysisRunStatus;
import com.memme.entity.sales.SalesDailyStatus;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.repository.ranking.RankingEntryRepository;
import com.memme.repository.ranking.RankingProfileRepository;
import com.memme.repository.ranking.RankingSnapshotRepository;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.service.sales.upload.SalesDailyStatusResolver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RankingGrowthQueryService {

    private static final ZoneId RANKING_ZONE = ZoneId.of("Asia/Seoul");
    private static final String NO_RANKING_MESSAGE = "아직 순위 정보가 없어요. 매출 데이터를 등록하면 랭킹에 참여할 수 있어요.";

    private final RankingSnapshotRepository snapshots;
    private final RankingEntryRepository entries;
    private final RankingProfileRepository profiles;
    private final StoreOwnershipRepository ownership;
    private final SalesDailySummaryRepository dailySummaries;
    private final StoreBusinessHoursRepository businessHours;
    private final AnalysisRunRepository analysisRuns;
    private final SalesDailyStatusResolver dailyStatusResolver;
    private final RankingSalesPeriodAggregator periodAggregator;
    private final Clock clock;

    public RankingGrowthQueryService(
            RankingSnapshotRepository snapshots,
            RankingEntryRepository entries,
            RankingProfileRepository profiles,
            StoreOwnershipRepository ownership,
            SalesDailySummaryRepository dailySummaries,
            StoreBusinessHoursRepository businessHours,
            AnalysisRunRepository analysisRuns,
            SalesDailyStatusResolver dailyStatusResolver,
            RankingSalesPeriodAggregator periodAggregator,
            Clock clock
    ) {
        this.snapshots = snapshots;
        this.entries = entries;
        this.profiles = profiles;
        this.ownership = ownership;
        this.dailySummaries = dailySummaries;
        this.businessHours = businessHours;
        this.analysisRuns = analysisRuns;
        this.dailyStatusResolver = dailyStatusResolver;
        this.periodAggregator = periodAggregator;
        this.clock = clock;
    }

    public RankingGrowthResponse query(Long userId, Long storeId) {
        if (!ownership.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SalesAnalysisRequestException(SalesAnalysisRequestException.Reason.STORE_OWNER_REQUIRED);
        }
        RankingPeriodCalculator.PeriodPair period = RankingPeriodCalculator.current(clock);
        Optional<RankingSnapshotEntity> completed = snapshots
                .findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndAndStatusOrderByRevisionNoDesc(
                        period.periodStart(), period.periodEnd(), period.comparisonStart(), period.comparisonEnd(),
                        RankingSnapshotStatus.COMPLETED);
        if (completed.isEmpty()) {
            return withoutCompletedSnapshot(period);
        }
        RankingSnapshotEntity snapshot = completed.get();
        List<RankingEntryEntity> orderedEntries = new ArrayList<>(
                entries.findAllByRankingSnapshotIdOrderByRankNoAsc(snapshot.getId()));
        orderedEntries.sort(Comparator.comparingInt(RankingEntryEntity::getRankNo)
                .thenComparing(RankingEntryEntity::getSelectedPeriodSales, Comparator.reverseOrder())
                .thenComparing(RankingEntryEntity::getRankingProfileId));
        Map<Long, RankingProfileEntity> profilesById = profiles.findAllById(orderedEntries.stream()
                        .map(RankingEntryEntity::getRankingProfileId).toList()).stream()
                .collect(Collectors.toMap(RankingProfileEntity::getId, Function.identity()));
        Long ownProfileId = profiles.findByStoreId(storeId).map(RankingProfileEntity::getId).orElse(null);
        RankingEntryEntity ownEntry = orderedEntries.stream()
                .filter(entry -> entry.getRankingProfileId().equals(ownProfileId))
                .findFirst().orElse(null);
        List<RankingGrowthResponse.Ranking> topTwenty = new ArrayList<>();
        for (RankingEntryEntity entry : orderedEntries.stream().limit(20).toList()) {
            boolean mine = entry.getRankingProfileId().equals(ownProfileId);
            RankingProfileEntity profile = profilesById.get(entry.getRankingProfileId());
            topTwenty.add(new RankingGrowthResponse.Ranking(
                    entry.getRankNo(), mine ? "내 매장" : profile.getAnonymousNickname(),
                    displayGrowth(entry.getGrowthRate()), mine));
        }
        RankingGrowthResponse.MyRanking myRanking = ownEntry == null ? null
                : new RankingGrowthResponse.MyRanking(ownEntry.getRankNo(),
                        displayGrowth(ownEntry.getGrowthRate()), orderedEntries.indexOf(ownEntry) < 20);
        RankingGrowthResponse.MyEligibility eligibility = ownEntry == null
                ? eligibility(storeId, period)
                : new RankingGrowthResponse.MyEligibility(RankingGrowthResponse.EligibilityStatus.ELIGIBLE, null);
        String message = ownEntry == null ? NO_RANKING_MESSAGE : "조회에 성공했습니다.";
        return new RankingGrowthResponse(message, RankingGrowthResponse.Status.COMPLETED,
                new RankingGrowthResponse.Data(true, responsePeriod(period, snapshot), topTwenty,
                        myRanking, eligibility));
    }

    private RankingGrowthResponse withoutCompletedSnapshot(RankingPeriodCalculator.PeriodPair period) {
        RankingSnapshotStatus latest = snapshots
                .findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndOrderByRevisionNoDesc(
                        period.periodStart(), period.periodEnd(), period.comparisonStart(), period.comparisonEnd())
                .map(RankingSnapshotEntity::getStatus).orElse(null);
        RankingGrowthResponse.Status status = switch (latest) {
            case PENDING, PROCESSING -> RankingGrowthResponse.Status.PROCESSING;
            case FAILED -> RankingGrowthResponse.Status.FAILED;
            case null -> RankingGrowthResponse.Status.NOT_CALCULATED;
            case COMPLETED -> RankingGrowthResponse.Status.NOT_CALCULATED;
        };
        String message = switch (status) {
            case PROCESSING -> "성장 랭킹을 집계하고 있습니다.";
            case FAILED -> "성장 랭킹을 집계하지 못했습니다. 잠시 후 다시 시도해주세요.";
            default -> "아직 성장 랭킹이 집계되지 않았어요.";
        };
        return new RankingGrowthResponse(message, status,
                new RankingGrowthResponse.Data(true, responsePeriod(period, null), List.of(), null,
                        new RankingGrowthResponse.MyEligibility(
                                RankingGrowthResponse.EligibilityStatus.UNKNOWN, null)));
    }

    private RankingGrowthResponse.Period responsePeriod(
            RankingPeriodCalculator.PeriodPair period, RankingSnapshotEntity snapshot
    ) {
        OffsetDateTime calculatedAt = snapshot == null || snapshot.getCalculatedAt() == null ? null
                : snapshot.getCalculatedAt().atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(RANKING_ZONE).toOffsetDateTime();
        return new RankingGrowthResponse.Period(RankingPeriod.LAST_MONTH,
                period.periodStart(), period.periodEnd(), period.comparisonStart(), period.comparisonEnd(),
                calculatedAt);
    }

    private BigDecimal displayGrowth(BigDecimal storedGrowth) {
        return storedGrowth.setScale(1, RoundingMode.HALF_UP);
    }

    private RankingGrowthResponse.MyEligibility eligibility(
            Long storeId, RankingPeriodCalculator.PeriodPair period
    ) {
        Map<LocalDate, RankingSalesPeriodAggregator.DailySales> dailySales = new HashMap<>();
        for (SalesDailySummaryEntity summary : dailySummaries
                .findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                        storeId, period.comparisonStart(), period.periodEnd())) {
            dailySales.put(summary.getSalesDate(), new RankingSalesPeriodAggregator.DailySales(
                    summary.getDayStatus(), summary.getTotalNetAmount()));
        }
        Set<LocalDate> closed = confirmedClosedDates(storeId, period);
        List<RankingSalesPeriodAggregator.AnalysisPeriod> completedAnalysis = analysisRuns
                .findAllOverlappingPeriod(storeId, AnalysisRunStatus.COMPLETED,
                        period.comparisonStart(), period.periodEnd()).stream()
                .map(run -> new RankingSalesPeriodAggregator.AnalysisPeriod(
                        run.getPeriodStart(), run.getPeriodEnd()))
                .toList();
        RankingGrowthResponse.EligibilityReason selectedReason = incompleteReason(
                period.periodStart(), period.periodEnd(), dailySales, closed, completedAnalysis,
                RankingGrowthResponse.EligibilityReason.SELECTED_PERIOD_MISSING);
        if (selectedReason != null) return insufficient(selectedReason);
        RankingGrowthResponse.EligibilityReason comparisonReason = incompleteReason(
                period.comparisonStart(), period.comparisonEnd(), dailySales, closed, completedAnalysis,
                RankingGrowthResponse.EligibilityReason.COMPARISON_PERIOD_MISSING);
        if (comparisonReason != null) return insufficient(comparisonReason);
        OptionalLong comparisonSales = periodAggregator.aggregate(period.comparisonStart(), period.comparisonEnd(),
                dailySales, closed, completedAnalysis);
        if (comparisonSales.isPresent() && comparisonSales.getAsLong() == 0) {
            return insufficient(RankingGrowthResponse.EligibilityReason.COMPARISON_SALES_ZERO);
        }
        return new RankingGrowthResponse.MyEligibility(RankingGrowthResponse.EligibilityStatus.UNKNOWN, null);
    }

    private RankingGrowthResponse.EligibilityReason incompleteReason(
            LocalDate start, LocalDate end,
            Map<LocalDate, RankingSalesPeriodAggregator.DailySales> dailySales,
            Set<LocalDate> closed,
            List<RankingSalesPeriodAggregator.AnalysisPeriod> analysis,
            RankingGrowthResponse.EligibilityReason absentReason
    ) {
        boolean hasData = false;
        boolean incomplete = false;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            RankingSalesPeriodAggregator.DailySales day = dailySales.get(date);
            if (day != null) hasData = true;
            if (day == null && !closed.contains(date)) incomplete = true;
            if (day != null
                    && (day.status() == SalesDailyStatus.MISSING
                    || day.status() == SalesDailyStatus.UNKNOWN)) incomplete = true;
        }
        if (!hasData && incomplete) return absentReason;
        if (incomplete) return RankingGrowthResponse.EligibilityReason.DATA_INCOMPLETE;
        if (periodAggregator.aggregate(start, end, dailySales, closed, analysis).isEmpty()) {
            return RankingGrowthResponse.EligibilityReason.ANALYSIS_NOT_COMPLETED;
        }
        return null;
    }

    private Set<LocalDate> confirmedClosedDates(Long storeId, RankingPeriodCalculator.PeriodPair period) {
        Map<Integer, StoreBusinessHours> hoursByWeekday = new HashMap<>();
        for (StoreBusinessHours hours : businessHours.findAllByStoreIdOrderByDayOfWeekAsc(storeId)) {
            hoursByWeekday.put(hours.getDayOfWeek(), hours);
        }
        Set<LocalDate> closed = new HashSet<>();
        for (LocalDate date = period.comparisonStart(); !date.isAfter(period.periodEnd()); date = date.plusDays(1)) {
            StoreBusinessHours hours = hoursByWeekday.get(date.getDayOfWeek().getValue());
            if (hours != null && Boolean.TRUE.equals(dailyStatusResolver.confirmedClosedStatus(
                    date, hours.getUpdatedAt(), hours.isClosed()))) {
                closed.add(date);
            }
        }
        return closed;
    }

    private RankingGrowthResponse.MyEligibility insufficient(RankingGrowthResponse.EligibilityReason reason) {
        return new RankingGrowthResponse.MyEligibility(
                RankingGrowthResponse.EligibilityStatus.DATA_INSUFFICIENT, reason);
    }
}
