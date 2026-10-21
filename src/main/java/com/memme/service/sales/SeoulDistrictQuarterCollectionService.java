package com.memme.service.sales;

import com.memme.dto.solution.external.SeoulEstimatedSalesResponse;
import com.memme.dto.solution.external.SeoulFootTrafficResponse;
import com.memme.dto.solution.external.SeoulStoreCountResponse;
import com.memme.service.sales.insight.SalesInsightRegenerationService;
import com.memme.service.solution.external.SeoulDistrictApiService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SeoulDistrictQuarterCollectionService {

    private static final Logger log = LoggerFactory.getLogger(SeoulDistrictQuarterCollectionService.class);
    private static final int PAGE_SIZE = 1000;

    private final SeoulDistrictApiService api;
    private final SeoulDistrictQuarterAggregationService aggregation;
    private final SeoulDistrictQuarterImportPersistenceService persistence;
    private final SalesInsightRegenerationService insightRegenerationService;

    public SeoulDistrictQuarterCollectionService(SeoulDistrictApiService api,
                                                 SeoulDistrictQuarterAggregationService aggregation,
                                                 SeoulDistrictQuarterImportPersistenceService persistence,
                                                 SalesInsightRegenerationService insightRegenerationService) {
        this.api = api;
        this.aggregation = aggregation;
        this.persistence = persistence;
        this.insightRegenerationService = insightRegenerationService;
    }

    public boolean collect(String basisQuarter) {
        if (basisQuarter == null || !basisQuarter.matches("[0-9]{4}Q[1-4]")) {
            throw new IllegalArgumentException("basisQuarter must be YYYYQ1..YYYYQ4");
        }
        Optional<Long> importId = persistence.begin(basisQuarter);
        if (importId.isEmpty()) {
            return false;
        }
        long id = importId.get();
        String sourceQuarter = basisQuarter.replace("Q", "");
        try {
            List<SeoulEstimatedSalesResponse.Row> sales = readAll(
                    (start, end) -> api.estimatedSales(start, end, sourceQuarter),
                    response -> new Page<>(response.data().totalCount(), response.data().row()));
            List<SeoulStoreCountResponse.Row> stores = readAll(
                    (start, end) -> api.storeCount(start, end, sourceQuarter),
                    response -> new Page<>(response.data().totalCount(), response.data().row()));
            List<SeoulFootTrafficResponse.Row> traffic = readAll(api::footTraffic,
                    response -> new Page<>(response.data().totalCount(), response.data().row()));
            var benchmarks = aggregation.aggregate(basisQuarter, sales, stores, traffic);
            if (benchmarks.isEmpty()) {
                throw new IllegalStateException("Seoul benchmark data unavailable");
            }
            persistence.complete(id, benchmarks);
            log.info("Seoul district quarter collection completed: quarter={}, benchmarkRows={}",
                    basisQuarter, benchmarks.size());
        } catch (RuntimeException exception) {
            persistence.fail(id);
            throw exception;
        }
        try {
            insightRegenerationService.regenerateAfterDistrictQuarterImport(basisQuarter);
        } catch (RuntimeException exception) {
            log.error("Sales insight regeneration after Seoul district import failed: quarter={}, cause={}",
                    basisQuarter, exception.getClass().getSimpleName());
        }
        return true;
    }

    private static <T, R> List<R> readAll(BiFunction<Integer, Integer, Optional<T>> fetch,
                                          Function<T, Page<R>> unwrap) {
        List<R> result = new ArrayList<>();
        Integer expectedTotal = null;
        for (int start = 1; ; ) {
            int end = expectedTotal == null ? PAGE_SIZE : (int) Math.min((long) expectedTotal, (long) start + PAGE_SIZE - 1);
            T response = fetch.apply(start, end).orElseThrow(() -> new IllegalStateException("Seoul data unavailable"));
            Page<R> page = unwrap.apply(response);
            if (page.total() == null || page.total() < 1 || page.rows() == null
                    || page.rows().size() != Math.min((long) page.total() - start + 1, (long) end - start + 1)) {
                throw new IllegalStateException("Seoul data incomplete");
            }
            if (expectedTotal == null) {
                expectedTotal = page.total();
            } else if (!expectedTotal.equals(page.total())) {
                throw new IllegalStateException("Seoul data count changed during collection");
            }
            result.addAll(page.rows());
            if (end >= expectedTotal) {
                return List.copyOf(result);
            }
            start = end + 1;
        }
    }

    private record Page<R>(Integer total, List<R> rows) {}
}
