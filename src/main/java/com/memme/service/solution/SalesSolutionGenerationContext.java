package com.memme.service.solution;

import com.memme.dto.sales.SalesSolutionMetrics;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesAnalysisEntity;

record SalesSolutionGenerationContext(
        AnalysisRunEntity analysisRun,
        SalesAnalysisEntity salesAnalysis,
        SalesSolutionMetrics metrics
) {}
