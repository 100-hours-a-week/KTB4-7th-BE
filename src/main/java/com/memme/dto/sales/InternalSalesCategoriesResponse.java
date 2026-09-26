package com.memme.dto.sales;

import java.math.BigDecimal;
import java.util.List;

public record InternalSalesCategoriesResponse(
        List<Category> categories,
        List<MenuRanking> menuRankings
) {
    public record Category(String categoryName, BigDecimal menuSales, BigDecimal ratio) {
    }

    public record MenuRanking(int rank, String menuName, BigDecimal menuSales, long quantity) {
    }
}
