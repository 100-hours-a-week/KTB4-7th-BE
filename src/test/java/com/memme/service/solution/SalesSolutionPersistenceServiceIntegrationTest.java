package com.memme.service.solution;

import java.time.LocalDate;
import java.util.List;

import com.memme.dto.sales.SalesSolutionGenerationResponse;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.solution.SolutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SalesSolutionPersistenceServiceIntegrationTest {

    private static final long STORE_ID = 98_001L;
    private static final long ANALYSIS_ID = 98_056L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 24);

    private final SalesSolutionPersistenceService service;
    private final SolutionBundleRepository bundleRepository;
    private final SolutionRepository solutionRepository;

    @Autowired
    SalesSolutionPersistenceServiceIntegrationTest(
            SalesSolutionPersistenceService service,
            SolutionBundleRepository bundleRepository,
            SolutionRepository solutionRepository
    ) {
        this.service = service;
        this.bundleRepository = bundleRepository;
        this.solutionRepository = solutionRepository;
    }

    @BeforeEach
    void cleanUp() {
        solutionRepository.deleteAll();
        bundleRepository.deleteAll();
    }

    @Test
    void persistsAllCardsAndCompletesBundleInOneTransaction() {
        var start = service.start(STORE_ID, ANALYSIS_ID, TARGET_DATE);

        service.complete(start.bundle().getId(), ANALYSIS_ID, data(
                card(1, "점심 프로모션"),
                card(2, "인기 메뉴 노출")
        ));

        var savedBundle = bundleRepository.findById(start.bundle().getId()).orElseThrow();
        var savedCards = solutionRepository.findAllBySolutionBundleIdOrderByRankNoAsc(
                savedBundle.getId()
        );
        assertThat(savedBundle.getStatus()).isEqualTo(SolutionBundleStatus.COMPLETED);
        assertThat(savedCards).extracting(card -> card.getRankNo()).containsExactly(1, 2);
        assertThat(savedCards).extracting(card -> card.getEvidenceText())
                .containsExactly("근거 1", "근거 2");
    }

    @Test
    void rollsBackEveryCardAndBundleStatusWhenOneCardViolatesUniqueRank() {
        var start = service.start(STORE_ID, ANALYSIS_ID, TARGET_DATE);
        Long bundleId = start.bundle().getId();

        assertThatThrownBy(() -> service.complete(bundleId, ANALYSIS_ID, data(
                card(1, "첫 번째"),
                card(1, "중복 순위")
        ))).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(solutionRepository.findAllBySolutionBundleIdOrderByRankNoAsc(bundleId)).isEmpty();
        assertThat(bundleRepository.findById(bundleId).orElseThrow().getStatus())
                .isEqualTo(SolutionBundleStatus.GENERATING);
    }

    @Test
    void returnsExistingCompletedBundleWithoutCreatingDuplicate() {
        var first = service.start(STORE_ID, ANALYSIS_ID, TARGET_DATE);
        service.complete(first.bundle().getId(), ANALYSIS_ID, data(card(1, "첫 생성")));

        var second = service.start(STORE_ID, ANALYSIS_ID, TARGET_DATE);

        assertThat(second.shouldGenerate()).isFalse();
        assertThat(second.bundle().getId()).isEqualTo(first.bundle().getId());
        assertThat(bundleRepository.count()).isEqualTo(1);
        assertThat(solutionRepository.count()).isEqualTo(1);
    }

    @Test
    void restartsFailedBundleForLatestAnalysisWithoutCreatingDuplicate() {
        var first = service.start(STORE_ID, ANALYSIS_ID, TARGET_DATE);
        service.fail(first.bundle().getId());

        var restarted = service.start(STORE_ID, ANALYSIS_ID + 1, TARGET_DATE);

        assertThat(restarted.shouldGenerate()).isTrue();
        assertThat(restarted.bundle().getId()).isEqualTo(first.bundle().getId());
        assertThat(restarted.bundle().getSalesAnalysisId()).isEqualTo(ANALYSIS_ID + 1);
        assertThat(restarted.bundle().getStatus()).isEqualTo(SolutionBundleStatus.GENERATING);
        assertThat(bundleRepository.count()).isEqualTo(1);
    }

    private SalesSolutionGenerationResponse.Data data(
            SalesSolutionGenerationResponse.SolutionCard... cards
    ) {
        return new SalesSolutionGenerationResponse.Data(
                TARGET_DATE,
                List.of(cards),
                "claude-sonnet-4-5"
        );
    }

    private SalesSolutionGenerationResponse.SolutionCard card(int rankNo, String title) {
        return new SalesSolutionGenerationResponse.SolutionCard(
                rankNo,
                title,
                "요약 " + rankNo,
                "상세 " + rankNo,
                "근거 " + rankNo
        );
    }
}
