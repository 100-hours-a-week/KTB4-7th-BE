package com.memme.entity.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SalesForecastEntityTest {

    @Test
    void 공식_ERD의_예측_출처와_모델_정보를_보관한다() {
        SalesForecastEntity forecast = forecast(10L, 1_200_000, 1_000_000, 1_400_000);

        assertThat(forecast.getBasedOnUploadId()).isEqualTo(10L);
        assertThat(forecast.getBasisDate()).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(forecast.getModelVersion()).isEqualTo("ridge-2026-09-16");
        assertThat(forecast.getGeneratedAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 23, 0, 0, 5));
    }

    @Test
    void 예상값은_하한과_상한_사이에_있어야_한다() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> forecast(10L, 900_000, 1_000_000, 1_400_000)
        );
    }

    @Test
    void 모델_버전은_최대_50자다() {
        assertThatIllegalArgumentException().isThrownBy(() -> SalesForecastEntity.create(
                1L,
                10L,
                LocalDate.of(2026, 9, 24),
                LocalDate.of(2026, 9, 23),
                1_200_000,
                1_000_000,
                1_400_000,
                "x".repeat(51),
                LocalDateTime.of(2026, 9, 23, 0, 0, 5)
        ));
    }

    private SalesForecastEntity forecast(
            Long basedOnUploadId,
            long predictedSalesAmount,
            long lowerBound,
            long upperBound
    ) {
        return SalesForecastEntity.create(
                1L,
                basedOnUploadId,
                LocalDate.of(2026, 9, 24),
                LocalDate.of(2026, 9, 23),
                predictedSalesAmount,
                lowerBound,
                upperBound,
                "ridge-2026-09-16",
                LocalDateTime.of(2026, 9, 23, 0, 0, 5)
        );
    }
}
