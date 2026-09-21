package com.memme.dto.sales;

import java.math.BigDecimal;
import java.util.List;

public record SalesCategoriesResponse(
        List<Category> categories,
        List<MenuRanking> menuRankings
) {

    public record Category(String categoryName, BigDecimal netSales, BigDecimal ratio) {}

    public record MenuRanking(int rank, String menuName, BigDecimal netSales, long quantity) {}
}
