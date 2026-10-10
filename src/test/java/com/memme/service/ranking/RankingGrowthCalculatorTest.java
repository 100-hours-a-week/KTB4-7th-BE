package com.memme.service.ranking;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingGrowthCalculatorTest {

    private final RankingGrowthCalculator calculator = new RankingGrowthCalculator();

    @Test
    void sortsByExactGrowthThenSelectedSalesAndAssignsCompetitionRanks() {
        List<RankingGrowthCalculator.RankedStore> rankings = calculator.rank(List.of(
                new RankingGrowthCalculator.StoreSales(1L, 128L, 100L),
                new RankingGrowthCalculator.StoreSales(2L, 256L, 200L),
                new RankingGrowthCalculator.StoreSales(3L, 128L, 100L),
                new RankingGrowthCalculator.StoreSales(4L, 120L, 100L),
                new RankingGrowthCalculator.StoreSales(5L, 300L, 0L)
        ));

        assertThat(rankings)
                .extracting(RankingGrowthCalculator.RankedStore::storeId)
                .containsExactly(2L, 1L, 3L, 4L);
        assertThat(rankings)
                .extracting(RankingGrowthCalculator.RankedStore::rankNo)
                .containsExactly(1, 2, 2, 4);
        assertThat(rankings.get(0).growthRate()).isEqualByComparingTo(new BigDecimal("28.0000"));
    }

    @Test
    void roundsStoredGrowthRateToFourDecimalPlacesWithoutChangingTieOrder() {
        List<RankingGrowthCalculator.RankedStore> rankings = calculator.rank(List.of(
                new RankingGrowthCalculator.StoreSales(1L, 1_284_500L, 1_000_000L),
                new RankingGrowthCalculator.StoreSales(2L, 1_284_499L, 1_000_000L)
        ));

        assertThat(rankings.get(0).growthRate()).isEqualByComparingTo(new BigDecimal("28.4500"));
        assertThat(rankings.get(0).rankNo()).isEqualTo(1);
        assertThat(rankings.get(1).rankNo()).isEqualTo(2);
    }
}
