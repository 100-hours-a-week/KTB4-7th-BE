package com.memme.service.sales.forecast;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import com.memme.repository.sales.SalesForecastRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SalesForecastService {

    private final SalesForecastRepository forecastRepository;

    public SalesForecastService(SalesForecastRepository forecastRepository) {
        this.forecastRepository = forecastRepository;
    }

    public Optional<SalesForecastResult> findByStoreIdAndTargetDate(
            Long storeId,
            LocalDate targetDate
    ) {
        Objects.requireNonNull(storeId, "storeId");
        Objects.requireNonNull(targetDate, "targetDate");
        return forecastRepository.findByStoreIdAndTargetDate(storeId, targetDate)
                .map(SalesForecastResult::from);
    }

    @Transactional
    public SalesForecastResult upsert(SalesForecastUpsertCommand command) {
        Objects.requireNonNull(command, "command");
        forecastRepository.upsertIfLatest(
                command.storeId(),
                command.basedOnUploadId(),
                command.targetDate(),
                command.basisDate(),
                command.predictedSalesAmount(),
                command.lowerBound(),
                command.upperBound(),
                command.modelVersion(),
                command.generatedAt()
        );
        return forecastRepository.findByStoreIdAndTargetDate(command.storeId(), command.targetDate())
                .map(SalesForecastResult::from)
                .orElseThrow(() -> new IllegalStateException("upserted forecast was not found"));
    }
}
