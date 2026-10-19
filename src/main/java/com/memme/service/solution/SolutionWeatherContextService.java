package com.memme.service.solution;

import com.memme.dto.sales.SalesSolutionMetrics.WeatherContext;
import com.memme.entity.solution.WeatherForecastSummaryEntity;
import com.memme.entity.store.Store;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.solution.external.KmaVillageForecastService;
import com.memme.service.solution.external.WeatherContextSummaryService;
import com.memme.service.store.StoreCoordinates;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class SolutionWeatherContextService {

    private static final Logger log = LoggerFactory.getLogger(SolutionWeatherContextService.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BASIS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final StoreRepository storeRepository;
    private final StoreBusinessHoursRepository businessHoursRepository;
    private final KmaVillageForecastService forecastService;
    private final WeatherContextSummaryService summaryService;
    private final WeatherForecastSummaryPersistenceService persistenceService;
    private final Clock clock;

    public SolutionWeatherContextService(
            StoreRepository storeRepository,
            StoreBusinessHoursRepository businessHoursRepository,
            KmaVillageForecastService forecastService,
            WeatherContextSummaryService summaryService,
            WeatherForecastSummaryPersistenceService persistenceService,
            Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.businessHoursRepository = businessHoursRepository;
        this.forecastService = forecastService;
        this.summaryService = summaryService;
        this.persistenceService = persistenceService;
        this.clock = clock;
    }

    public Optional<WeatherContext> resolve(Long storeId, LocalDate targetDate) {
        try {
            return resolveForLookup(storeId, targetDate);
        } catch (RuntimeException exception) {
            log.warn("Weather context unavailable for store {} and date {}: {}",
                    storeId, targetDate, exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public Optional<WeatherContext> resolveForLookup(Long storeId, LocalDate targetDate) {
        if (!LocalDate.now(clock.withZone(KOREA)).equals(targetDate)) {
            return Optional.empty();
        }
            Optional<Store> store = storeRepository.findById(storeId);
            if (store.isEmpty() || store.get().getLatitude() == null || store.get().getLongitude() == null) {
                return Optional.empty();
            }
            StoreCoordinates coordinates = new StoreCoordinates(
                    store.get().getLatitude(), store.get().getLongitude());
            var businessHours = businessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(storeId);
            var forecast = forecastService.fetch(coordinates);
            if (forecast.isEmpty()) {
                return Optional.empty();
            }
            Optional<WeatherContext> summary = summaryService.summarize(forecast.get(), businessHours);
            if (summary.isEmpty() && summaryService.needsDailyTemperatureSupplement(forecast.get(), targetDate)) {
                // Keep the latest issue for operating-hour weather; earlier issue supplies only today's TMN/TMX.
                var earlierForecast = forecastService.fetchEarlyIssue(coordinates, targetDate);
                if (earlierForecast.isPresent()) {
                    summary = summaryService.summarize(forecast.get(), earlierForecast.get(), businessHours);
                }
            }
            if (summary.isEmpty()) {
                return Optional.empty();
            }
            WeatherContext weather = summary.get();
            WeatherForecastSummaryEntity incoming = WeatherForecastSummaryEntity.create(
                    storeId, targetDate, LocalDateTime.parse(weather.basisDateTime(), BASIS),
                    weather.skyText(), weather.minTempC(), weather.maxTempC(),
                    BigDecimal.valueOf(weather.maxPop()), weather.rainHours(),
                    weather.precipitationMm() == null ? null : BigDecimal.valueOf(weather.precipitationMm()),
                    weather.isNotable(), LocalDateTime.now(clock.withZone(KOREA)));
            WeatherForecastSummaryEntity persisted;
            try {
                persisted = persistenceService.saveIfNewer(incoming);
            } catch (DataIntegrityViolationException duplicate) {
                // Another solution generation may have inserted the same store/date concurrently.
                persisted = persistenceService.saveIfNewer(incoming);
            }
            return Optional.of(toContext(persisted));
    }

    private WeatherContext toContext(WeatherForecastSummaryEntity summary) {
        return new WeatherContext(BASIS.format(summary.getBasisDatetime()), summary.getSkyText(),
                summary.getMinTempC(), summary.getMaxTempC(), summary.getMaxPop().doubleValue(),
                summary.getRainHours(), summary.getPrecipitationMm() == null
                        ? null : summary.getPrecipitationMm().doubleValue(), summary.isNotable());
    }
}
