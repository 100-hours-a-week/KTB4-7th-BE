package com.memme.service.sales.forecast;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.memme.repository.sales.SalesForecastRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SalesForecastServiceTest {

    private final SalesForecastService forecastService;
    private final SalesForecastRepository forecastRepository;

    @Autowired
    SalesForecastServiceTest(
            SalesForecastService forecastService,
            SalesForecastRepository forecastRepository
    ) {
        this.forecastService = forecastService;
        this.forecastRepository = forecastRepository;
    }

    @Test
    void 같은_업로드의_재실행은_같은_행을_갱신한다() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        SalesForecastResult first = forecastService.upsert(command(
                10L, targetDate, 1_200_000, "ridge-2026-09-16"
        ));
        SalesForecastResult updated = forecastService.upsert(command(
                10L, targetDate, 1_300_000, "ridge-2026-09-17"
        ));

        assertThat(updated.id()).isEqualTo(first.id());
        assertThat(updated.predictedSalesAmount()).isEqualTo(1_300_000L);
        assertThat(updated.modelVersion()).isEqualTo("ridge-2026-09-17");
        assertThat(forecastRepository.count()).isEqualTo(1);
    }

    @Test
    void 더_최신_업로드의_예측으로_교체한다() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        forecastService.upsert(command(10L, targetDate, 1_200_000, "ridge-2026-09-16"));

        SalesForecastResult updated = forecastService.upsert(command(
                11L, targetDate, 1_300_000, "ridge-2026-09-17"
        ));

        assertThat(updated.basedOnUploadId()).isEqualTo(11L);
        assertThat(updated.predictedSalesAmount()).isEqualTo(1_300_000L);
        assertThat(forecastRepository.count()).isEqualTo(1);
    }

    @Test
    void 늦게_도착한_이전_업로드_응답은_최신_예측을_덮어쓰지_않는다() {
        LocalDate targetDate = LocalDate.of(2026, 9, 24);
        forecastService.upsert(command(11L, targetDate, 1_300_000, "ridge-2026-09-17"));

        SalesForecastResult retained = forecastService.upsert(command(
                10L, targetDate, 900_000, "ridge-2026-09-16"
        ));

        assertThat(retained.basedOnUploadId()).isEqualTo(11L);
        assertThat(retained.predictedSalesAmount()).isEqualTo(1_300_000L);
        assertThat(retained.modelVersion()).isEqualTo("ridge-2026-09-17");
    }

    private SalesForecastUpsertCommand command(
            Long basedOnUploadId,
            LocalDate targetDate,
            long predictedSalesAmount,
            String modelVersion
    ) {
        return new SalesForecastUpsertCommand(
                1L,
                basedOnUploadId,
                targetDate,
                targetDate.minusDays(1),
                predictedSalesAmount,
                predictedSalesAmount - 200_000,
                predictedSalesAmount + 200_000,
                modelVersion,
                LocalDateTime.of(2026, 9, 23, 0, 0, 5)
        );
    }
}
