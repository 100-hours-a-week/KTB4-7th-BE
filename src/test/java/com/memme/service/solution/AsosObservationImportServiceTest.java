package com.memme.service.solution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.entity.auth.User;
import com.memme.entity.solution.WeatherObservationEntity;
import com.memme.entity.solution.WeatherStationEntity;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreStatus;
import com.memme.repository.solution.WeatherObservationRepository;
import com.memme.repository.solution.WeatherStationRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.solution.external.KmaAsosObservationClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AsosObservationImportServiceTest {

    @Test
    void 정오_관측을_날짜별로_저장하고_이미_저장된_날짜는_중복_적재하지_않는다() {
        var conditions = org.mockito.Mockito.mock(com.memme.repository.solution.WeatherCalendarConditionRepository.class);
        var withNoon = new AsosObservationImportService(stores, stations, observations, conditions, client,
                Clock.fixed(Instant.parse("2026-10-18T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        Store store = store(20L, "37.5714", "126.9658");
        store.updateWeatherStationId(7L);
        var station = WeatherStationEntity.create("KMA_ASOS", "108", new BigDecimal("37.5714"), new BigDecimal("126.9658"));
        ReflectionTestUtils.setField(station, "id", 7L);
        when(stores.findAllByStatus(StoreStatus.ACTIVE)).thenReturn(List.of(store));
        when(client.stations()).thenReturn(List.of(station("108", "37.5714", "126.9658")));
        when(stations.findById(7L)).thenReturn(Optional.of(station));
        when(stations.findBySourceAndStationCode("KMA_ASOS", "108")).thenReturn(Optional.of(station));
        LocalDate date = LocalDate.of(2026, 10, 17);
        when(client.dailyNoonWeather(eq("108"), any(), any())).thenReturn(Map.of(date,
                new KmaAsosObservationClient.NoonWeather(com.memme.entity.solution.WeatherCalendarCondition.RAIN, date.atTime(12, 0))));
        ArgumentCaptor<List<com.memme.entity.solution.WeatherCalendarConditionEntity>> captor = ArgumentCaptor.forClass(List.class);
        withNoon.importForActiveStores();
        verify(conditions).saveAll(captor.capture());
        var saved = captor.getValue().getFirst();
        assertThat(saved.getStationId()).isEqualTo(7L);
        assertThat(saved.getObservedAt()).isEqualTo(date.atTime(12, 0));
        when(conditions.findAllByStationIdAndObservationDateBetweenOrderByObservationDateAsc(eq(7L), any(), any()))
                .thenReturn(List.of(saved));
        withNoon.importForActiveStores();
        verify(conditions, org.mockito.Mockito.times(1)).saveAll(any());
    }

    @Mock private StoreRepository stores;
    @Mock private WeatherStationRepository stations;
    @Mock private WeatherObservationRepository observations;
    @Mock private KmaAsosObservationClient client;

    private AsosObservationImportService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-18T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new AsosObservationImportService(stores, stations, observations, client, clock);
    }

    @Test
    void 기존_매장을_20km_이내_최근접_관측소에_연결하고_0mm를_저장한다() {
        Store store = store(20L, "37.4786", "126.8646");
        when(stores.findAllByStatus(StoreStatus.ACTIVE)).thenReturn(List.of(store));
        when(client.stations()).thenReturn(List.of(
                station("108", "37.5714", "126.9658"),
                station("112", "37.4777", "126.6249")));
        when(stations.findBySourceAndStationCode("KMA_ASOS", "108")).thenReturn(Optional.empty());
        when(stations.save(any())).thenAnswer(invocation -> {
            WeatherStationEntity saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });
        when(observations.findByStationIdAndObservationDateBetweenOrderByObservationDateAsc(eq(7L), any(), any()))
                .thenReturn(List.of());
        when(client.dailyRain(eq("108"), any(), any()))
                .thenReturn(Map.of(LocalDate.of(2026, 10, 17), BigDecimal.ZERO));

        service.importForActiveStores();

        assertThat(store.getWeatherStationId()).isEqualTo(7L);
        ArgumentCaptor<List<WeatherObservationEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(observations).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(row -> {
            assertThat(row.getObservationDate()).isEqualTo(LocalDate.of(2026, 10, 17));
            assertThat(row.getPrecipitationMm()).isEqualByComparingTo("0");
        });
    }

    @Test
    void 이십킬로미터_밖의_관측소는_매핑하지_않는다() {
        Store store = store(20L, "37.4786", "126.8646");
        when(stores.findAllByStatus(StoreStatus.ACTIVE)).thenReturn(List.of(store));
        when(client.stations()).thenReturn(List.of(station("112", "37.4777", "126.6249")));

        service.importForActiveStores();

        assertThat(store.getWeatherStationId()).isNull();
        verify(stations, never()).save(any());
        verify(observations, never()).saveAll(any());
    }

    @Test
    void 기존_ASOS_매핑도_현재_후보가_20km_밖이면_해제한다() {
        Store store = store(20L, "37.4786", "126.8646");
        store.updateWeatherStationId(7L);
        WeatherStationEntity savedStation = WeatherStationEntity.create("KMA_ASOS", "112",
                new BigDecimal("37.4777"), new BigDecimal("126.6249"));
        ReflectionTestUtils.setField(savedStation, "id", 7L);
        when(stores.findAllByStatus(StoreStatus.ACTIVE)).thenReturn(List.of(store));
        when(client.stations()).thenReturn(List.of(station("112", "37.4777", "126.6249")));
        when(stations.findById(7L)).thenReturn(Optional.of(savedStation));

        service.importForActiveStores();

        assertThat(store.getWeatherStationId()).isNull();
        verify(stores).save(store);
    }

    @Test
    void 이미_저장된_날짜는_재적재하지_않는다() {
        Store store = store(20L, "37.4786", "126.8646");
        store.updateWeatherStationId(7L);
        WeatherStationEntity savedStation = WeatherStationEntity.create("KMA_ASOS", "108",
                new BigDecimal("37.5714"), new BigDecimal("126.9658"));
        ReflectionTestUtils.setField(savedStation, "id", 7L);
        when(stores.findAllByStatus(StoreStatus.ACTIVE)).thenReturn(List.of(store));
        when(client.stations()).thenReturn(List.of(station("108", "37.5714", "126.9658")));
        when(stations.findBySourceAndStationCode("KMA_ASOS", "108")).thenReturn(Optional.of(savedStation));
        when(observations.findByStationIdAndObservationDateBetweenOrderByObservationDateAsc(eq(7L), any(), any()))
                .thenReturn(List.of(WeatherObservationEntity.create(7L, LocalDate.of(2026, 10, 17),
                        savedStation.getLatitude(), savedStation.getLongitude(), "RAIN", null,
                        BigDecimal.ONE, "KMA_ASOS")));
        when(client.dailyRain(eq("108"), any(), any()))
                .thenReturn(Map.of(LocalDate.of(2026, 10, 17), BigDecimal.ONE));

        service.importForActiveStores();

        verify(observations, never()).saveAll(any());
    }

    @Test
    void 기존_결측_행이_있어도_유니크_키가_중복되지_않는다() {
        Store store = store(20L, "37.4786", "126.8646");
        WeatherStationEntity savedStation = WeatherStationEntity.create("KMA_ASOS", "108",
                new BigDecimal("37.5714"), new BigDecimal("126.9658"));
        ReflectionTestUtils.setField(savedStation, "id", 7L);
        when(stores.findAllByStatus(StoreStatus.ACTIVE)).thenReturn(List.of(store));
        when(client.stations()).thenReturn(List.of(station("108", "37.5714", "126.9658")));
        when(stations.findBySourceAndStationCode("KMA_ASOS", "108")).thenReturn(Optional.of(savedStation));
        when(observations.findByStationIdAndObservationDateBetweenOrderByObservationDateAsc(eq(7L), any(), any()))
                .thenReturn(List.of(WeatherObservationEntity.create(7L, LocalDate.of(2026, 10, 17),
                        savedStation.getLatitude(), savedStation.getLongitude(), "UNKNOWN", null,
                        null, "KMA_ASOS")));
        when(client.dailyRain(eq("108"), any(), any()))
                .thenReturn(Map.of(LocalDate.of(2026, 10, 17), BigDecimal.ONE));

        service.importForActiveStores();

        verify(observations, never()).saveAll(any());
    }

    private Store store(Long id, String latitude, String longitude) {
        Store store = Store.create(User.create("owner@example.com", "hash", "01012345678",
                        LocalDateTime.of(2026, 1, 1, 0, 0)), null, null,
                "매장", "12345", "서울", null, LocalDateTime.of(2026, 1, 1, 0, 0));
        ReflectionTestUtils.setField(store, "id", id);
        store.updateCoordinates(new BigDecimal(latitude), new BigDecimal(longitude));
        return store;
    }

    private KmaAsosObservationClient.Station station(String code, String latitude, String longitude) {
        return new KmaAsosObservationClient.Station(code, new BigDecimal(latitude), new BigDecimal(longitude));
    }
}
