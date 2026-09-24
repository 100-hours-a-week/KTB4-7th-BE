package com.memme.repository.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.memme.entity.sales.SalesForecastEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SalesForecastRepositoryTest {

    private final SalesForecastRepository forecastRepository;

    @Autowired
    SalesForecastRepositoryTest(SalesForecastRepository forecastRepository) {
        this.forecastRepository = forecastRepository;
    }

    @Test
    void 매장과_대상일자로_예측을_조회한다() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        forecastRepository.save(SalesForecastEntity.create(
                1L,
                10L,
                targetDate,
                targetDate.minusDays(1),
                1_200_000,
                1_000_000,
                1_400_000,
                "ridge-2026-09-16",
                LocalDateTime.of(2026, 9, 23, 0, 0, 5)
        ));

        assertThat(forecastRepository.findByStoreIdAndTargetDate(1L, targetDate))
                .get()
                .extracting(SalesForecastEntity::getPredictedSalesAmount)
                .isEqualTo(1_200_000L);
        assertThat(forecastRepository.findByStoreIdAndTargetDate(2L, targetDate)).isEmpty();
    }
}
