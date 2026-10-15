package com.memme.repository.solution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.memme.entity.solution.WeatherForecastSummaryEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class WeatherForecastSummaryRepositoryTest {

    @Autowired
    private WeatherForecastSummaryRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 매장과_달력일자로_예보_요약을_저장하고_조회한다() {
        LocalDate targetDate = LocalDate.of(2026, 10, 15);
        repository.saveAndFlush(WeatherForecastSummaryEntity.create(
                1L, targetDate, LocalDateTime.of(2026, 10, 15, 11, 0), "비",
                14, 24, new BigDecimal("0.700"), List.of(15, 16, 17),
                null, true, LocalDateTime.of(2026, 10, 15, 11, 15)
        ));
        entityManager.clear();

        assertThat(repository.findByStoreIdAndTargetDate(1L, targetDate))
                .get()
                .satisfies(summary -> {
                    assertThat(summary.getBasisDatetime()).isEqualTo(LocalDateTime.of(2026, 10, 15, 11, 0));
                    assertThat(summary.getMaxPop()).isEqualByComparingTo("0.700");
                    assertThat(summary.getRainHours()).containsExactly(15, 16, 17);
                    assertThat(summary.getPrecipitationMm()).isNull();
                    assertThat(summary.isNotable()).isTrue();
                });
        assertThat(repository.findByStoreIdAndTargetDate(2L, targetDate)).isEmpty();
    }

    @Test
    void 더_최신_발표_예보로만_같은_매장과_날짜의_요약을_갱신한다() {
        LocalDate targetDate = LocalDate.of(2026, 10, 15);
        WeatherForecastSummaryEntity summary = repository.saveAndFlush(WeatherForecastSummaryEntity.create(
                1L, targetDate, LocalDateTime.of(2026, 10, 15, 8, 0), "맑음",
                14, 24, new BigDecimal("0.100"), List.of(),
                BigDecimal.ZERO, false, LocalDateTime.of(2026, 10, 15, 8, 15)
        ));
        Long id = summary.getId();

        assertThat(repository.updateIfNewer(WeatherForecastSummaryEntity.create(
                1L, targetDate, LocalDateTime.of(2026, 10, 15, 11, 0), "비",
                13, 22, new BigDecimal("0.700"), List.of(15, 16),
                null, true, LocalDateTime.of(2026, 10, 15, 11, 15)
        ))).isEqualTo(1);
        assertThat(repository.updateIfNewer(WeatherForecastSummaryEntity.create(
                1L, targetDate, LocalDateTime.of(2026, 10, 15, 5, 0), "맑음",
                15, 25, BigDecimal.ZERO, List.of(),
                BigDecimal.ZERO, false, LocalDateTime.of(2026, 10, 15, 12, 0)
        ))).isZero();
        assertThat(repository.findByStoreIdAndTargetDate(1L, targetDate)).get()
                .extracting(WeatherForecastSummaryEntity::getSkyText)
                .isEqualTo("비");
        entityManager.clear();

        assertThat(repository.findByStoreIdAndTargetDate(1L, targetDate)).get()
                .satisfies(saved -> {
                    assertThat(saved.getId()).isEqualTo(id);
                    assertThat(saved.getBasisDatetime()).isEqualTo(LocalDateTime.of(2026, 10, 15, 11, 0));
                    assertThat(saved.getSkyText()).isEqualTo("비");
                    assertThat(saved.getMaxPop()).isEqualByComparingTo("0.700");
                    assertThat(saved.getRainHours()).containsExactly(15, 16);
                    assertThat(saved.getPrecipitationMm()).isNull();
                    assertThat(saved.isNotable()).isTrue();
                    assertThat(saved.getFetchedAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 11, 15));
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 외부_트랜잭션이_없어도_조건부_갱신이_실행된다() {
        LocalDate targetDate = LocalDate.of(2026, 10, 16);
        WeatherForecastSummaryEntity summary = repository.saveAndFlush(WeatherForecastSummaryEntity.create(
                3L, targetDate, LocalDateTime.of(2026, 10, 16, 8, 0), "맑음",
                10, 20, new BigDecimal("0.100"), List.of(),
                BigDecimal.ZERO, false, LocalDateTime.of(2026, 10, 16, 8, 15)
        ));
        try {
            assertThat(repository.updateIfNewer(WeatherForecastSummaryEntity.create(
                    3L, targetDate, LocalDateTime.of(2026, 10, 16, 11, 0), "비",
                    10, 19, new BigDecimal("0.700"), List.of(15),
                    null, true, LocalDateTime.of(2026, 10, 16, 11, 15)
            ))).isEqualTo(1);
        } finally {
            repository.deleteById(summary.getId());
        }
    }
}
