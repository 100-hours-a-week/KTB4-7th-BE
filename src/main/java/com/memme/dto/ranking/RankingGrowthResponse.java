package com.memme.dto.ranking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record RankingGrowthResponse(String message, Status status, Data data) {

    public enum Status {
        COMPLETED,
        NOT_CALCULATED,
        PROCESSING,
        FAILED
    }

    public record Data(
            boolean isFinal,
            Period period,
            List<Ranking> rankings,
            MyRanking myRanking,
            MyEligibility myEligibility
    ) {
        public Data {
            if (!isFinal) {
                throw new IllegalArgumentException("지난달 랭킹 기간은 종료된 상태여야 합니다.");
            }
            rankings = List.copyOf(rankings);
        }
    }

    public record Period(
            RankingPeriod type,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate comparisonStartDate,
            LocalDate comparisonEndDate,
            OffsetDateTime calculatedAt
    ) {
    }

    public record Ranking(int rank, String displayName, BigDecimal growthRate, boolean isMine) {
    }

    public record MyRanking(int rank, BigDecimal growthRate, boolean includedInTop20) {
    }

    public record MyEligibility(EligibilityStatus status, EligibilityReason reason) {
    }

    public enum EligibilityStatus {
        ELIGIBLE,
        DATA_INSUFFICIENT,
        UNKNOWN
    }

    public enum EligibilityReason {
        SELECTED_PERIOD_MISSING,
        COMPARISON_PERIOD_MISSING,
        DATA_INCOMPLETE,
        COMPARISON_SALES_ZERO,
        ANALYSIS_NOT_COMPLETED
    }
}
