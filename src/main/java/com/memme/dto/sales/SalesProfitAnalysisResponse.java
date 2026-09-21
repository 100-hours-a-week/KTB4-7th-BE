package com.memme.dto.sales;

import java.math.BigDecimal;
import java.util.List;

public record SalesProfitAnalysisResponse(
        BigDecimal netSales,
        BigDecimal ingredientCost,
        BigDecimal fixedCost,
        BigDecimal netProfit,
        List<Composition> composition
) {

    public record Composition(String type, BigDecimal amount, BigDecimal ratio) {}
}
