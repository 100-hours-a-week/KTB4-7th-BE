package com.memme.service.solution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.dto.sales.SalesSolutionGenerationResponse;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.entity.solution.SolutionEntity;
import com.memme.entity.solution.SolutionType;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.solution.SolutionRegenerationRepository;
import com.memme.repository.solution.SolutionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SolutionRegenerationPersistenceServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 21);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);
    private static final String SOURCE = "a".repeat(64);

    @Autowired private SolutionRegenerationPersistenceService service;
    @Autowired private SolutionBundleRepository bundles;
    @Autowired private SolutionRepository cards;
    @Autowired private SolutionRegenerationRepository executions;

    @Test
    void reservesOnceAndAtomicallyArchivesOldCardWhileKeepingItsId() {
        Long bundleId = completedBundle(91001L);
        Long oldCardId = cards.findAllBySolutionBundleIdOrderByRankNoAsc(bundleId).getFirst().getId();

        var first = service.reserve(91001L, DATE, 100L, SOURCE, NOW);
        assertThat(first.outcome()).isEqualTo(SolutionRegenerationOutcome.RESERVED);
        assertThat(service.reserve(91001L, DATE, 100L, SOURCE, NOW.plusMinutes(1)).outcome())
                .isEqualTo(SolutionRegenerationOutcome.IN_PROGRESS);

        service.complete(91001L, DATE, 100L, 101L, first, candidate("new"), NOW.plusMinutes(2));

        assertThat(cards.findAllBySolutionBundleIdOrderByRankNoAsc(bundleId))
                .extracting(SolutionEntity::getTitle).containsExactly("new");
        assertThat(cards.findById(oldCardId)).get().satisfies(old -> {
            assertThat(old.getTitle()).isEqualTo("old");
            assertThat(old.getRankNo()).isEqualTo(1);
            assertThat(old.getStorageRankNo()).isGreaterThan(1);
            assertThat(old.isActive()).isFalse();
        });
        assertThat(bundles.findById(bundleId).orElseThrow().getSalesAnalysisId()).isEqualTo(101L);
        assertThat(service.reserve(91001L, DATE, 101L, SOURCE, NOW.plusMinutes(3)).outcome())
                .isEqualTo(SolutionRegenerationOutcome.ALREADY_COMPLETED);
        assertThat(service.reserve(91001L, DATE, 101L, "b".repeat(64), NOW.plusMinutes(4)).outcome())
                .isEqualTo(SolutionRegenerationOutcome.RESERVED);
    }

    @Test
    void failedCandidateLeavesCompletedCardsAndCanRetry() {
        Long bundleId = completedBundle(91002L);
        var first = service.reserve(91002L, DATE, 100L, SOURCE, NOW);

        assertThatThrownBy(() -> service.complete(91002L, DATE, 999L, 101L, first,
                candidate("new"), NOW.plusMinutes(1))).isInstanceOf(IllegalStateException.class);
        service.fail(first, NOW.plusMinutes(1));

        assertThat(cards.findAllBySolutionBundleIdOrderByRankNoAsc(bundleId))
                .extracting(SolutionEntity::getTitle).containsExactly("old");
        assertThat(bundles.findById(bundleId).orElseThrow().getStatus())
                .isEqualTo(SolutionBundleStatus.COMPLETED);
        assertThat(service.reserve(91002L, DATE, 100L, SOURCE, NOW.plusMinutes(2)).attempt())
                .isEqualTo(2);
    }

    @Test
    void anotherSourceCannotStartWhileTargetIsProcessing() {
        completedBundle(91003L);
        service.reserve(91003L, DATE, 100L, SOURCE, NOW);

        assertThat(service.reserve(91003L, DATE, 100L, "b".repeat(64), NOW.plusMinutes(1)).outcome())
                .isEqualTo(SolutionRegenerationOutcome.IN_PROGRESS);
        assertThat(executions.findByStoreIdAndTargetDateAndSourceFingerprint(
                91003L, DATE, "b".repeat(64))).isEmpty();
    }

    private Long completedBundle(Long storeId) {
        var bundle = SolutionBundleEntity.create(storeId, 100L, DATE);
        bundle.changeStatus(SolutionBundleStatus.COMPLETED);
        Long bundleId = bundles.saveAndFlush(bundle).getId();
        cards.saveAndFlush(SolutionEntity.create(storeId, 100L, bundleId, SolutionType.DAILY,
                "old", "summary", "detail", null, NOW.minusHours(1), 1));
        return bundleId;
    }

    private SalesSolutionGenerationResponse.Data candidate(String title) {
        return new SalesSolutionGenerationResponse.Data(DATE,
                List.of(new SalesSolutionGenerationResponse.SolutionCard(
                        1, title, "summary", "detail", null)), "model");
    }
}
