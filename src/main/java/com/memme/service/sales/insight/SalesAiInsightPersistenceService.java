package com.memme.service.sales.insight;

import java.time.YearMonth;
import java.util.List;

import com.memme.entity.sales.SalesAiInsightEntity;
import com.memme.repository.sales.SalesAiInsightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesAiInsightPersistenceService {

    private final SalesAiInsightRepository repository;

    public SalesAiInsightPersistenceService(SalesAiInsightRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(Long storeId, Long salesAnalysisId, YearMonth targetMonth) {
        SalesAiInsightEntity entity = repository.findByStoreIdAndTargetMonth(storeId, targetMonth)
                .map(existing -> {
                    existing.restart(salesAnalysisId);
                    return existing;
                })
                .orElseGet(() -> SalesAiInsightEntity.pending(
                        storeId,
                        salesAnalysisId,
                        targetMonth
                ));
        entity.startGenerating();
        return repository.saveAndFlush(entity).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long insightId, List<String> insights) {
        find(insightId).complete(insights);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInsufficientData(Long insightId) {
        find(insightId).markInsufficientData();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long insightId) {
        find(insightId).fail();
    }

    private SalesAiInsightEntity find(Long insightId) {
        return repository.findById(insightId)
                .orElseThrow(() -> new IllegalStateException(
                        "매출 AI 인사이트를 찾을 수 없습니다: " + insightId
                ));
    }
}
