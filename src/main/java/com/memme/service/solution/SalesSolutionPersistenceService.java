package com.memme.service.solution;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.memme.dto.sales.SalesSolutionGenerationResponse;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.entity.solution.SolutionEntity;
import com.memme.entity.solution.SolutionType;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.solution.SolutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesSolutionPersistenceService {

    private final SolutionBundleRepository bundleRepository;
    private final SolutionRepository solutionRepository;

    public SalesSolutionPersistenceService(
            SolutionBundleRepository bundleRepository,
            SolutionRepository solutionRepository
    ) {
        this.bundleRepository = bundleRepository;
        this.solutionRepository = solutionRepository;
    }

    @Transactional
    public StartResult start(Long storeId, Long salesAnalysisId, LocalDate targetDate) {
        return bundleRepository.findByStoreIdAndTargetDate(storeId, targetDate)
                .map(bundle -> startExisting(bundle, salesAnalysisId))
                .orElseGet(() -> {
                    SolutionBundleEntity bundle = SolutionBundleEntity.create(
                            storeId,
                            salesAnalysisId,
                            targetDate
                    );
                    bundle.changeStatus(SolutionBundleStatus.GENERATING);
                    return new StartResult(bundleRepository.save(bundle), true);
                });
    }

    private StartResult startExisting(SolutionBundleEntity bundle, Long salesAnalysisId) {
        if (bundle.getStatus() == SolutionBundleStatus.FAILED) {
            bundle.restartForAnalysis(salesAnalysisId);
            return new StartResult(bundle, true);
        }
        return new StartResult(bundle, false);
    }

    @Transactional
    public void restart(Long bundleId, Long salesAnalysisId) {
        SolutionBundleEntity bundle = requireBundle(bundleId);
        bundle.restartForAnalysis(salesAnalysisId);
    }

    @Transactional
    public void complete(
            Long bundleId,
            Long expectedSalesAnalysisId,
            SalesSolutionGenerationResponse.Data data
    ) {
        SolutionBundleEntity bundle = requireBundle(bundleId);
        if (bundle.getStatus() != SolutionBundleStatus.GENERATING
                || !expectedSalesAnalysisId.equals(bundle.getSalesAnalysisId())) {
            throw new IllegalStateException("solution bundle generation state changed");
        }
        if (!solutionRepository.findAllBySolutionBundleIdOrderByRankNoAsc(bundleId).isEmpty()) {
            throw new IllegalStateException("solution bundle already has cards");
        }
        LocalDateTime generatedAt = LocalDateTime.now();
        List<SolutionEntity> cards = data.solutionCards().stream()
                .map(card -> SolutionEntity.create(
                        bundle.getStoreId(),
                        expectedSalesAnalysisId,
                        bundleId,
                        SolutionType.DAILY,
                        card.title(),
                        card.summaryText(),
                        card.detailText(),
                        card.evidence(),
                        generatedAt,
                        card.rankNo()
                ))
                .toList();
        solutionRepository.saveAll(cards);
        bundle.changeStatus(SolutionBundleStatus.COMPLETED);
    }

    @Transactional
    public void fail(Long bundleId) {
        bundleRepository.findById(bundleId)
                .ifPresent(bundle -> bundle.changeStatus(SolutionBundleStatus.FAILED));
    }

    private SolutionBundleEntity requireBundle(Long bundleId) {
        return bundleRepository.findById(bundleId)
                .orElseThrow(() -> new IllegalStateException("solution bundle not found"));
    }

    public record StartResult(SolutionBundleEntity bundle, boolean shouldGenerate) {}
}
