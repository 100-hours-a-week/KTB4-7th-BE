package com.memme.service.sales.analysis;

import java.math.BigDecimal;
import java.util.List;

import com.memme.dto.sales.SalesAnalysisResponse;
import com.memme.entity.sales.AnalysisMetricEntity;
import com.memme.entity.sales.AnalysisMetricUnit;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.MenuAnalysisResultEntity;
import com.memme.entity.sales.SalesAnalysisEntity;
import com.memme.repository.sales.AnalysisMetricRepository;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.MenuAnalysisResultRepository;
import com.memme.repository.sales.SalesAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesAnalysisSnapshotService {

    private final AnalysisRunRepository analysisRunRepository;
    private final SalesAnalysisRepository salesAnalysisRepository;
    private final AnalysisMetricRepository metricRepository;
    private final MenuAnalysisResultRepository menuResultRepository;

    public SalesAnalysisSnapshotService(
            AnalysisRunRepository analysisRunRepository,
            SalesAnalysisRepository salesAnalysisRepository,
            AnalysisMetricRepository metricRepository,
            MenuAnalysisResultRepository menuResultRepository
    ) {
        this.analysisRunRepository = analysisRunRepository;
        this.salesAnalysisRepository = salesAnalysisRepository;
        this.metricRepository = metricRepository;
        this.menuResultRepository = menuResultRepository;
    }

    @Transactional
    public Long persist(Long uploadId, SalesAnalysisResult result) {
        AnalysisRunEntity run = analysisRunRepository
                .findFirstByBasedOnUploadIdOrderByIdDesc(uploadId)
                .orElseThrow(() -> new IllegalStateException("analysis run not found"));
        return salesAnalysisRepository.findByAnalysisRunId(run.getId())
                .map(SalesAnalysisEntity::getId)
                .orElseGet(() -> persistNew(run.getId(), result));
    }

    private Long persistNew(Long analysisRunId, SalesAnalysisResult result) {
        SalesAnalysisEntity analysis = salesAnalysisRepository.save(
                SalesAnalysisEntity.create(analysisRunId, null)
        );
        if (result instanceof SalesAnalysisResult.Completed completed) {
            persistMetrics(analysis.getId(), completed.analysis());
            persistMenuResults(analysis.getId(), completed.analysis().menuRankings());
        }
        return analysis.getId();
    }

    private void persistMetrics(Long analysisId, SalesAnalysisResponse response) {
        SalesAnalysisResponse.Kpis kpis = response.kpis();
        metricRepository.saveAll(List.of(
                AnalysisMetricEntity.create(
                        analysisId,
                        "TOTAL_SALES",
                        kpis.totalSales(),
                        kpis.changes().totalSalesRate(),
                        AnalysisMetricUnit.KRW
                ),
                AnalysisMetricEntity.create(
                        analysisId,
                        "ORDER_COUNT",
                        BigDecimal.valueOf(kpis.orderCount()),
                        kpis.changes().orderCountRate(),
                        AnalysisMetricUnit.COUNT
                ),
                AnalysisMetricEntity.create(
                        analysisId,
                        "AVERAGE_ORDER_VALUE",
                        kpis.averageOrderValue(),
                        kpis.changes().averageOrderValueRate(),
                        AnalysisMetricUnit.KRW
                )
        ));
    }

    private void persistMenuResults(
            Long analysisId,
            List<SalesAnalysisResponse.MenuRanking> rankings
    ) {
        menuResultRepository.saveAll(rankings.stream()
                .map(ranking -> MenuAnalysisResultEntity.create(
                        analysisId,
                        null,
                        ranking.menuName(),
                        ranking.salesAmount().longValueExact(),
                        Math.toIntExact(ranking.quantity()),
                        ranking.rank()
                ))
                .toList());
    }
}
