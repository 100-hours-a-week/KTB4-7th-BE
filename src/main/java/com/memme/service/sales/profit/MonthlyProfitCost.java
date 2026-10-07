package com.memme.service.sales.profit;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

public record MonthlyProfitCost(
        YearMonth month,
        long rentAmount,
        long laborAmount,
        BigDecimal ingredientCostRate
) {

    public MonthlyProfitCost {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(ingredientCostRate, "ingredientCostRate");
        if (rentAmount < 0 || laborAmount < 0) {
            throw new IllegalArgumentException("월 임대료와 인건비는 0원 이상이어야 합니다.");
        }
        if (ingredientCostRate.signum() < 0
                || ingredientCostRate.compareTo(BigDecimal.ONE) > 0
                || ingredientCostRate.scale() > 4) {
            throw new IllegalArgumentException("재료 원가율은 0 이상 1 이하이고 소수점 넷째 자리까지만 입력할 수 있습니다.");
        }
    }
}
