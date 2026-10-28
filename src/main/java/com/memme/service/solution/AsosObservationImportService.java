package com.memme.service.solution;

import com.memme.entity.solution.WeatherObservationEntity;
import com.memme.entity.solution.WeatherStationEntity;
import com.memme.entity.solution.WeatherCalendarConditionEntity;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreStatus;
import com.memme.repository.solution.WeatherCalendarConditionRepository;
import com.memme.repository.solution.WeatherObservationRepository;
import com.memme.repository.solution.WeatherStationRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.solution.external.KmaAsosObservationClient;
import com.memme.service.solution.external.KmaAsosObservationClient.NoonWeather;
import com.memme.service.solution.external.KmaAsosObservationClient.Station;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AsosObservationImportService {

    private static final Logger log = LoggerFactory.getLogger(AsosObservationImportService.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final String SOURCE = "KMA_ASOS";
    private static final double MAX_DISTANCE_KM = 20.0;
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final StoreRepository stores;
    private final WeatherStationRepository stations;
    private final WeatherObservationRepository observations;
    private final WeatherCalendarConditionRepository calendarConditions;
    private final KmaAsosObservationClient client;
    private final Clock clock;

    @Autowired
    public AsosObservationImportService(StoreRepository stores, WeatherStationRepository stations,
                                        WeatherObservationRepository observations,
                                        WeatherCalendarConditionRepository calendarConditions,
                                        KmaAsosObservationClient client, Clock clock) {
        this.stores = stores;
        this.stations = stations;
        this.observations = observations;
        this.calendarConditions = calendarConditions;
        this.client = client;
        this.clock = clock;
    }

    public AsosObservationImportService(StoreRepository stores, WeatherStationRepository stations,
                                        WeatherObservationRepository observations,
                                        KmaAsosObservationClient client, Clock clock) {
        this(stores, stations, observations, null, client, clock);
    }

    public int importForActiveStores() {
        LocalDate end = LocalDate.now(clock.withZone(KOREA)).minusDays(1);
        LocalDate start = end.minusDays(83);
        List<Station> candidates;
        try {
            candidates = client.stations();
        } catch (RuntimeException exception) {
            log.warn("ASOS station catalog unavailable: {}", exception.getClass().getSimpleName());
            return 0;
        }

        Map<Long, ImportTarget> usedStations = new HashMap<>();
        for (Store store : stores.findAllByStatus(StoreStatus.ACTIVE)) {
            if (store.getLatitude() == null || store.getLongitude() == null) {
                continue;
            }
            try {
                WeatherStationEntity existing = store.getWeatherStationId() == null ? null
                        : stations.findById(store.getWeatherStationId()).orElse(null);
                if (existing != null && !SOURCE.equals(existing.getSource())) {
                    continue;
                }
                Station nearest = candidates.stream()
                        .min(Comparator.comparingDouble(station -> distanceKm(store, station)))
                        .orElse(null);
                if (nearest == null || distanceKm(store, nearest) > MAX_DISTANCE_KM) {
                    if (existing != null) {
                        store.updateWeatherStationId(null);
                        stores.save(store);
                    }
                    continue;
                }
                WeatherStationEntity station = stations.findBySourceAndStationCode(SOURCE, nearest.code())
                        .orElseGet(() -> stations.save(WeatherStationEntity.create(
                                SOURCE, nearest.code(), nearest.latitude(), nearest.longitude())));
                if (!station.getId().equals(store.getWeatherStationId())) {
                    store.updateWeatherStationId(station.getId());
                    stores.save(store);
                }
                LocalDate calendarStart = store.getCreatedAt() == null
                        ? start : store.getCreatedAt().toLocalDate();
                usedStations.merge(station.getId(), new ImportTarget(station, calendarStart),
                        (existingTarget, incomingTarget) -> incomingTarget.startDate()
                                .isBefore(existingTarget.startDate()) ? incomingTarget : existingTarget);
            } catch (RuntimeException exception) {
                log.warn("ASOS station mapping failed for store {}: {}",
                        store.getId(), exception.getClass().getSimpleName());
            }
        }

        int inserted = 0;
        for (ImportTarget target : usedStations.values()) {
            try {
                inserted += importMissing(target.station(), start, end);
            } catch (RuntimeException exception) {
                log.warn("ASOS observation import failed for station {}: {}",
                        target.station().getStationCode(), exception.getClass().getSimpleName());
            }
            try {
                inserted += importMissingNoonConditions(target.station(), target.startDate(), end);
            } catch (RuntimeException exception) {
                log.warn("ASOS noon weather import failed for station {}: {}",
                        target.station().getStationCode(), exception.getClass().getSimpleName());
            }
        }
        return inserted;
    }

    private int importMissingNoonConditions(WeatherStationEntity station, LocalDate start, LocalDate end) {
        if (calendarConditions == null || start.isAfter(end)) {
            return 0;
        }
        Set<LocalDate> savedDates = new HashSet<>();
        for (WeatherCalendarConditionEntity saved : calendarConditions
                .findAllByStationIdAndObservationDateBetweenOrderByObservationDateAsc(
                        station.getId(), start, end)) {
            savedDates.add(saved.getObservationDate());
        }
        if (savedDates.size() == java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1) return 0;
        Map<LocalDate, NoonWeather> noonWeather = client.dailyNoonWeather(
                station.getStationCode(), start, end);
        List<WeatherCalendarConditionEntity> fresh = new ArrayList<>();
        for (Map.Entry<LocalDate, NoonWeather> entry : noonWeather.entrySet()) {
            if (entry.getKey().isBefore(start) || entry.getKey().isAfter(end)
                    || savedDates.contains(entry.getKey())) {
                continue;
            }
            NoonWeather observation = entry.getValue();
            fresh.add(WeatherCalendarConditionEntity.create(
                    station.getId(), entry.getKey(), observation.condition(), observation.observedAt(),
                    "KMA_ASOS_HOURLY"));
        }
        if (!fresh.isEmpty()) {
            calendarConditions.saveAll(fresh);
        }
        return fresh.size();
    }

    private int importMissing(WeatherStationEntity station, LocalDate start, LocalDate end) {
        Set<LocalDate> savedDates = new HashSet<>();
        for (WeatherObservationEntity saved : observations
                .findByStationIdAndObservationDateBetweenOrderByObservationDateAsc(station.getId(), start, end)) {
            savedDates.add(saved.getObservationDate());
        }
        List<WeatherObservationEntity> fresh = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); ) {
            if (savedDates.contains(day)) {
                day = day.plusDays(1);
                continue;
            }
            LocalDate firstMissing = day;
            while (!day.isAfter(end) && !savedDates.contains(day)) {
                day = day.plusDays(1);
            }
            LocalDate lastMissing = day.minusDays(1);
            Map<LocalDate, BigDecimal> rainfall = client.dailyRain(station.getStationCode(),
                    firstMissing, lastMissing);
            for (Map.Entry<LocalDate, BigDecimal> entry : rainfall.entrySet()) {
                if (entry.getKey().isBefore(firstMissing) || entry.getKey().isAfter(lastMissing)
                        || savedDates.contains(entry.getKey())) {
                    continue;
                }
                BigDecimal amount = entry.getValue();
                if (amount == null || amount.signum() < 0) {
                    continue;
                }
                fresh.add(WeatherObservationEntity.create(station.getId(), entry.getKey(),
                        station.getLatitude(), station.getLongitude(), amount.signum() == 0 ? "NO_RAIN" : "RAIN",
                        null, amount, SOURCE));
            }
        }
        if (!fresh.isEmpty()) {
            observations.saveAll(fresh);
        }
        return fresh.size();
    }

    private double distanceKm(Store store, Station station) {
        double latitude1 = Math.toRadians(store.getLatitude().doubleValue());
        double latitude2 = Math.toRadians(station.latitude().doubleValue());
        double deltaLatitude = latitude2 - latitude1;
        double deltaLongitude = Math.toRadians(station.longitude().doubleValue()
                - store.getLongitude().doubleValue());
        double halfChord = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(latitude1) * Math.cos(latitude2) * Math.pow(Math.sin(deltaLongitude / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(halfChord), Math.sqrt(1 - halfChord));
    }

    private record ImportTarget(WeatherStationEntity station, LocalDate startDate) {
    }
}
