package com.memme.dto.ranking;

public record RankingGrowthRequest(RankingPeriod period) {

    public RankingGrowthRequest {
        period = period == null ? RankingPeriod.LAST_MONTH : period;
    }
}
