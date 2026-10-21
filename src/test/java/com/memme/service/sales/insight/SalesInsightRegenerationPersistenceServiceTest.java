package com.memme.service.sales.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.entity.sales.SalesAiInsightEntity;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.repository.sales.SalesAiInsightRegenerationRepository;
import com.memme.repository.sales.SalesAiInsightRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SalesInsightRegenerationPersistenceServiceTest {
    private static final YearMonth MONTH = YearMonth.of(2026, 9);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);
    private static final String SOURCE = "c".repeat(64);

    @Autowired private SalesInsightRegenerationPersistenceService service;
    @Autowired private SalesAiInsightRepository insights;
    @Autowired private SalesAiInsightRegenerationRepository executions;
    @Autowired private SalesInsightRegenerationSourceFingerprint fingerprints;

    @Test
    void fingerprintIsStableForSameMonthAndSource() {
        assertThat(fingerprints.of(100L, MONTH, List.of("RAIN")))
                .isEqualTo(fingerprints.of(100L, MONTH, List.of("RAIN")))
                .matches("[0-9a-f]{64}");
    }

    @Test
    void replacesCompletedInsightOnlyAfterCandidateIsReady() {
        Long id = completedInsight(92001L);
        var reservation = service.reserve(92001L, MONTH, 100L, SOURCE, NOW);
        assertThat(reservation.outcome()).isEqualTo(SalesInsightRegenerationOutcome.RESERVED);
        assertThat(service.reserve(92001L, MONTH, 100L, SOURCE, NOW.plusMinutes(1)).outcome())
                .isEqualTo(SalesInsightRegenerationOutcome.IN_PROGRESS);

        service.complete(92001L, MONTH, 100L, 101L, reservation, List.of("new"), NOW.plusMinutes(2));

        var updated = insights.findById(id).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(SalesAiInsightStatus.COMPLETED);
        assertThat(updated.getInsights()).containsExactly("new");
        assertThat(updated.getSalesAnalysisId()).isEqualTo(101L);
        assertThat(service.reserve(92001L, MONTH, 101L, SOURCE, NOW.plusMinutes(3)).outcome())
                .isEqualTo(SalesInsightRegenerationOutcome.ALREADY_COMPLETED);
        assertThat(service.reserve(92001L, MONTH, 101L, "d".repeat(64), NOW.plusMinutes(4)).outcome())
                .isEqualTo(SalesInsightRegenerationOutcome.RESERVED);
    }

    @Test
    void invalidCandidateKeepsExistingInsightAndCanRetry() {
        Long id = completedInsight(92002L);
        var reservation = service.reserve(92002L, MONTH, 100L, SOURCE, NOW);

        assertThatThrownBy(() -> service.complete(92002L, MONTH, 100L, 101L,
                reservation, List.of(" "), NOW.plusMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        service.fail(reservation, NOW.plusMinutes(1));

        assertThat(insights.findById(id).orElseThrow().getInsights()).containsExactly("old");
        assertThat(service.reserve(92002L, MONTH, 100L, SOURCE, NOW.plusMinutes(2)).attempt())
                .isEqualTo(2);
    }

    @Test
    void differentSourceCannotRunConcurrently() {
        completedInsight(92003L);
        service.reserve(92003L, MONTH, 100L, SOURCE, NOW);
        assertThat(service.reserve(92003L, MONTH, 100L, "d".repeat(64), NOW.plusMinutes(1)).outcome())
                .isEqualTo(SalesInsightRegenerationOutcome.IN_PROGRESS);
        assertThat(executions.findByStoreIdAndTargetMonthAndSourceFingerprint(
                92003L, MONTH.atDay(1), "d".repeat(64))).isEmpty();
    }

    private Long completedInsight(Long storeId) {
        var insight = SalesAiInsightEntity.pending(storeId, 100L, MONTH);
        insight.startGenerating();
        insight.complete(List.of("old"));
        return insights.saveAndFlush(insight).getId();
    }
}
