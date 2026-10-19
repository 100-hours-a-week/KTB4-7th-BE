package com.memme.service.chat;

import com.memme.dto.chat.ChatDistrictBenchmarkResponse;
import com.memme.dto.chat.ChatLookupResponse;
import com.memme.dto.chat.ChatWeatherResponse;
import com.memme.dto.sales.DistrictBenchmarkMetric;
import com.memme.dto.sales.SalesSolutionMetrics.WeatherContext;
import com.memme.entity.solution.WeatherForecastSummaryEntity;
import com.memme.repository.solution.WeatherForecastSummaryRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.sales.insight.DistrictBenchmarkService;
import com.memme.service.solution.SolutionWeatherContextService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatSalesLookupService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BASIS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final StoreRepository stores;
    private final WeatherForecastSummaryRepository forecasts;
    private final SolutionWeatherContextService weatherContextService;
    private final DistrictBenchmarkService districtBenchmarkService;
    private final Clock clock;

    public ChatSalesLookupService(StoreRepository stores,
                                  WeatherForecastSummaryRepository forecasts,
                                  SolutionWeatherContextService weatherContextService,
                                  DistrictBenchmarkService districtBenchmarkService,
                                  Clock clock) {
        this.stores = stores;
        this.forecasts = forecasts;
        this.weatherContextService = weatherContextService;
        this.districtBenchmarkService = districtBenchmarkService;
        this.clock = clock;
    }

    public ChatLookupResponse<ChatWeatherResponse> weather(Long storeId, LocalDate targetDate) {
        requireStore(storeId);
        LocalDate date = targetDate == null ? LocalDate.now(clock.withZone(KOREA)) : targetDate;
        Optional<WeatherForecastSummaryEntity> saved = forecasts.findByStoreIdAndTargetDate(storeId, date);
        if (saved.isPresent()) {
            WeatherForecastSummaryEntity value = saved.get();
            return ChatLookupResponse.ready(new ChatWeatherResponse(
                    BASIS.format(value.getBasisDatetime()), value.getSkyText(),
                    (int) value.getMinTempC(), (int) value.getMaxTempC(),
                    value.getMaxPop().doubleValue(), value.getRainHours(),
                    value.getPrecipitationMm() == null ? null : value.getPrecipitationMm().doubleValue(),
                    value.isNotable()));
        }
        return weatherContextService.resolveForLookup(storeId, date)
                .map(this::weatherResponse)
                .map(ChatLookupResponse::ready)
                .orElseGet(ChatLookupResponse::empty);
    }

    public ChatLookupResponse<ChatDistrictBenchmarkResponse> districtBenchmark(Long storeId) {
        requireStore(storeId);
        YearMonth currentMonth = YearMonth.now(clock.withZone(KOREA));
        return districtBenchmarkService.findForInsight(storeId, currentMonth)
                .map(this::districtResponse)
                .map(ChatLookupResponse::ready)
                .orElseGet(ChatLookupResponse::empty);
    }

    private ChatWeatherResponse weatherResponse(WeatherContext value) {
        return new ChatWeatherResponse(value.basisDateTime(), value.skyText(), value.minTempC(),
                value.maxTempC(), value.maxPop(), value.rainHours(), value.precipitationMm(),
                value.isNotable());
    }

    private ChatDistrictBenchmarkResponse districtResponse(DistrictBenchmarkMetric value) {
        return new ChatDistrictBenchmarkResponse(value.basisQuarter(),
                value.districtAvgMonthlySalesPerCafe(), value.cafeStoreCount(),
                value.peakSalesHourBand(), value.peakFootTrafficHourBand());
    }

    private void requireStore(Long storeId) {
        if (storeId == null || storeId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storeId must be positive");
        }
        if (!stores.existsById(storeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found");
        }
    }
}
