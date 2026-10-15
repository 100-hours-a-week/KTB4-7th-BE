package com.memme.service.solution;

import com.memme.entity.solution.WeatherForecastSummaryEntity;
import com.memme.repository.solution.WeatherForecastSummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeatherForecastSummaryPersistenceService {

    private final WeatherForecastSummaryRepository repository;

    public WeatherForecastSummaryPersistenceService(WeatherForecastSummaryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WeatherForecastSummaryEntity saveIfNewer(WeatherForecastSummaryEntity incoming) {
        if (repository.updateIfNewer(incoming) > 0) {
            return repository.findByStoreIdAndTargetDate(incoming.getStoreId(), incoming.getTargetDate())
                    .orElseThrow();
        }
        return repository.findByStoreIdAndTargetDate(incoming.getStoreId(), incoming.getTargetDate())
                .orElseGet(() -> repository.saveAndFlush(incoming));
    }
}
