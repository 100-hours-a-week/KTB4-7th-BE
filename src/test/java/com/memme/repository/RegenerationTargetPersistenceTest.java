package com.memme.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.entity.sales.SalesAiInsightEntity;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.repository.sales.SalesAiInsightRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RegenerationTargetPersistenceTest {

    @Autowired private SalesAiInsightRepository insights;
    @Autowired private SolutionBundleRepository bundles;
    @Autowired private EntityManager entityManager;

    @Test
    void completedInsightCanBeLockedAndReplacedWithoutPendingStatus() {
        var original = SalesAiInsightEntity.pending(500L, 100L, YearMonth.of(2026, 9));
        original.startGenerating();
        original.complete(List.of("old"));
        Long id = insights.saveAndFlush(original).getId();
        entityManager.clear();

        var locked = insights.findByStoreIdAndTargetMonthForUpdate(500L,
                LocalDate.of(2026, 9, 1)).orElseThrow();
        assertThat(entityManager.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        locked.replaceCompleted(100L, 101L, List.of("new"),
                LocalDateTime.of(2026, 10, 21, 12, 0));
        insights.flush();
        entityManager.clear();

        var replaced = insights.findByIdForUpdate(id).orElseThrow();
        assertThat(replaced.getStatus()).isEqualTo(SalesAiInsightStatus.COMPLETED);
        assertThat(replaced.getSalesAnalysisId()).isEqualTo(101L);
        assertThat(replaced.getInsights()).containsExactly("new");
        assertThat(entityManager.getLockMode(replaced)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void completedInsightRejectsStaleSource() {
        var original = SalesAiInsightEntity.pending(501L, 100L, YearMonth.of(2026, 9));
        original.startGenerating();
        original.complete(List.of("old"));
        assertThatThrownBy(() -> original.replaceCompleted(99L, 101L, List.of("new"),
                LocalDateTime.of(2026, 10, 21, 12, 0)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(original.getInsights()).containsExactly("old");
    }

    @Test
    void completedBundleCanBeLockedAndPointToNewAnalysis() {
        var original = SolutionBundleEntity.create(502L, 100L, LocalDate.of(2026, 10, 21));
        original.changeStatus(SolutionBundleStatus.COMPLETED);
        bundles.saveAndFlush(original);
        entityManager.clear();

        var locked = bundles.findByStoreIdAndTargetDateForUpdate(502L,
                LocalDate.of(2026, 10, 21)).orElseThrow();
        assertThat(entityManager.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        locked.replaceCompletedAnalysis(100L, 101L);
        bundles.flush();
        assertThat(locked.getSalesAnalysisId()).isEqualTo(101L);
        assertThat(locked.getStatus()).isEqualTo(SolutionBundleStatus.COMPLETED);
    }
}
