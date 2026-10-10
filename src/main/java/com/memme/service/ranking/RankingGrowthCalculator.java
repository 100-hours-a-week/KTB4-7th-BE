package com.memme.service.ranking;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public class RankingGrowthCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigInteger ONE_HUNDRED_INTEGER = BigInteger.valueOf(100);
    private static final int STORED_SCALE = 4;

    public List<RankedStore> rank(List<StoreSales> storeSales) {
        List<StoreSales> eligible = storeSales.stream()
                .filter(sales -> sales.comparisonPeriodSales() > 0)
                .sorted(this::compareForRanking)
                .toList();

        List<RankedStore> rankings = new ArrayList<>(eligible.size());
        int previousRank = 0;
        StoreSales previous = null;
        for (int index = 0; index < eligible.size(); index++) {
            StoreSales current = eligible.get(index);
            int rank = index + 1;
            if (previous != null
                    && compareGrowthRate(previous, current) == 0
                    && previous.selectedPeriodSales() == current.selectedPeriodSales()) {
                rank = previousRank;
            }
            rankings.add(new RankedStore(
                    current.storeId(),
                    rank,
                    growthRate(current).setScale(STORED_SCALE, RoundingMode.HALF_UP),
                    current.selectedPeriodSales(),
                    current.comparisonPeriodSales()
            ));
            previous = current;
            previousRank = rank;
        }
        return List.copyOf(rankings);
    }

    private int compareForRanking(StoreSales left, StoreSales right) {
        int growthComparison = compareGrowthRate(right, left);
        if (growthComparison != 0) {
            return growthComparison;
        }
        int selectedSalesComparison = Long.compare(right.selectedPeriodSales(), left.selectedPeriodSales());
        if (selectedSalesComparison != 0) {
            return selectedSalesComparison;
        }
        return Long.compare(left.storeId(), right.storeId());
    }

    private int compareGrowthRate(StoreSales left, StoreSales right) {
        BigInteger leftDifference = BigInteger.valueOf(left.selectedPeriodSales())
                .subtract(BigInteger.valueOf(left.comparisonPeriodSales()));
        BigInteger rightDifference = BigInteger.valueOf(right.selectedPeriodSales())
                .subtract(BigInteger.valueOf(right.comparisonPeriodSales()));
        BigInteger leftCrossProduct = leftDifference.multiply(BigInteger.valueOf(right.comparisonPeriodSales()))
                .multiply(ONE_HUNDRED_INTEGER);
        BigInteger rightCrossProduct = rightDifference.multiply(BigInteger.valueOf(left.comparisonPeriodSales()))
                .multiply(ONE_HUNDRED_INTEGER);
        return leftCrossProduct.compareTo(rightCrossProduct);
    }

    private BigDecimal growthRate(StoreSales sales) {
        return BigDecimal.valueOf(sales.selectedPeriodSales())
                .subtract(BigDecimal.valueOf(sales.comparisonPeriodSales()))
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(sales.comparisonPeriodSales()), STORED_SCALE, RoundingMode.HALF_UP);
    }

    public record StoreSales(Long storeId, long selectedPeriodSales, long comparisonPeriodSales) {

        public StoreSales {
            Objects.requireNonNull(storeId, "storeId");
        }
    }

    public record RankedStore(
            Long storeId,
            int rankNo,
            BigDecimal growthRate,
            long selectedPeriodSales,
            long comparisonPeriodSales
    ) {
    }
}
