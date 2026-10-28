package com.memme.service.sales.analysis;

import com.memme.repository.sales.SalesAiInsightRepository;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesForecastRepository;
import com.memme.repository.sales.SalesOrderRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Read in the same repeatable-read snapshot as the analysis; Redis is never the version authority. */
@Service
public class SalesAnalysisCacheVersion {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final SalesDailySummaryRepository summaries;
    private final SalesOrderRepository orders;
    private final SalesForecastRepository forecasts;
    private final SalesAiInsightRepository insights;

    public SalesAnalysisCacheVersion(SalesDailySummaryRepository summaries, SalesOrderRepository orders,
            SalesForecastRepository forecasts, SalesAiInsightRepository insights) {
        this.summaries = summaries;
        this.orders = orders;
        this.forecasts = forecasts;
        this.insights = insights;
    }

    public String read(Long storeId, LocalDate start, LocalDate end) {
        // Include all summary dates, since comparison periods can fall outside the requested period.
        var days = summaries.findAllByStoreIdOrderBySalesDateAsc(storeId).stream().map(day -> List.of(
                day.getId(), day.getSalesDate(), day.getTotalNetAmount(), day.getMenuNetAmount(),
                day.getOrderCount(), day.getMenuQuantity(), day.getDayStatus(), day.getUpdatedAt())).toList();
        // Upload replacement inserts new immutable order/item rows, even when their totals are unchanged.
        long orderGeneration = orders.findMaximumIdByStoreId(storeId).orElse(0L);
        var predictions = forecasts.findAllByStoreIdAndTargetDateBetweenOrderByTargetDateAsc(storeId, start, end)
                .stream().map(f -> List.of(f.getId(), f.getBasedOnUploadId(), f.getTargetDate(),
                        f.getPredictedSalesAmount(), f.getLowerBound(), f.getUpperBound(),
                        f.getModelVersion(), f.getGeneratedAt())).toList();
        var insight = insights.findByStoreIdAndTargetMonth(storeId, YearMonth.from(end))
                .map(i -> new InsightVersion(i.getId(), i.getSalesAnalysisId(), i.getStatus().name(),
                        i.getInsights(), i.getGeneratedAt())).orElse(null);
        return digest(JSON.writeValueAsString(new SourceVersion(days, orderGeneration, predictions, insight)));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record SourceVersion(Object days, long orderGeneration, Object forecasts, InsightVersion insight) {}
    private record InsightVersion(Long id, Long analysisId, String status, List<String> insights,
                                  java.time.LocalDateTime generatedAt) {}
}
