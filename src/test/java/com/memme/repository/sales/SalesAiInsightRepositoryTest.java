package com.memme.repository.sales;

import java.time.YearMonth;
import java.util.List;

import com.memme.entity.sales.SalesAiInsightEntity;
import com.memme.entity.sales.SalesAiInsightStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SalesAiInsightRepositoryTest {

    private final SalesAiInsightRepository repository;

    @Autowired
    SalesAiInsightRepositoryTest(SalesAiInsightRepository repository) {
        this.repository = repository;
    }

    @Test
    void storesMonthlyInsightAsJsonAndFindsItByStoreAndMonth() {
        SalesAiInsightEntity insight = SalesAiInsightEntity.pending(
                1L,
                56L,
                YearMonth.of(2026, 9)
        );
        insight.startGenerating();
        insight.complete(List.of("9월 총 매출은 7,920,000원입니다."));
        repository.saveAndFlush(insight);

        SalesAiInsightEntity found = repository.findByStoreIdAndTargetMonth(
                1L,
                YearMonth.of(2026, 9)
        ).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(SalesAiInsightStatus.COMPLETED);
        assertThat(found.getInsights()).containsExactly("9월 총 매출은 7,920,000원입니다.");
        assertThat(found.getGeneratedAt()).isNotNull();
    }

    @Test
    void restartsExistingMonthWithLatestSalesAnalysis() {
        SalesAiInsightEntity completed = SalesAiInsightEntity.pending(
                1L,
                56L,
                YearMonth.of(2026, 9)
        );
        completed.startGenerating();
        completed.complete(List.of("이전 인사이트입니다."));
        repository.saveAndFlush(completed);
        Long originalInsightId = completed.getId();

        SalesAiInsightEntity existing = repository.findByStoreIdAndTargetMonth(
                1L,
                YearMonth.of(2026, 9)
        ).orElseThrow();
        existing.restart(57L);
        existing.startGenerating();
        Long insightId = repository.saveAndFlush(existing).getId();

        SalesAiInsightEntity restarted = repository.findById(insightId).orElseThrow();
        assertThat(insightId).isEqualTo(originalInsightId);
        assertThat(restarted.getSalesAnalysisId()).isEqualTo(57L);
        assertThat(restarted.getStatus()).isEqualTo(SalesAiInsightStatus.GENERATING);
        assertThat(restarted.getInsights()).isNull();
        assertThat(restarted.getGeneratedAt()).isNull();
    }
}
