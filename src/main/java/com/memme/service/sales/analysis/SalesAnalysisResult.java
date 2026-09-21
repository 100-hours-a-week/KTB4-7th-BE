package com.memme.service.sales.analysis;

import com.memme.dto.sales.SalesAnalysisResponse;
import com.memme.dto.sales.SalesCategoriesResponse;

public sealed interface SalesAnalysisResult
        permits SalesAnalysisResult.Completed, SalesAnalysisResult.Empty {

    record Completed(
            SalesAnalysisResponse analysis,
            SalesCategoriesResponse categories
    ) implements SalesAnalysisResult {}

    record Empty(SalesAnalysisResponse.EmptyData analysis) implements SalesAnalysisResult {}
}
