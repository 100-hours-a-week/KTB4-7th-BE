package com.memme.repository.solution;

import com.memme.entity.auth.User;
import com.memme.entity.solution.WeatherObservationEntity;
import com.memme.entity.solution.WeatherStationEntity;
import com.memme.entity.store.Store;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.store.StoreRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class WeatherObservationRepositoryTest {

    @Autowired WeatherStationRepository stations;
    @Autowired WeatherObservationRepository observations;
    @Autowired StoreRepository stores;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    @Test
    void 관측_지점별_날짜_범위의_실측_강수량을_조회한다() {
        var station = stations.saveAndFlush(WeatherStationEntity.create(
                "KMA_ASOS", "108", new BigDecimal("37.5714000"), new BigDecimal("126.9658000")));
        var other = stations.saveAndFlush(WeatherStationEntity.create(
                "KMA_ASOS", "112", new BigDecimal("37.4777000"), new BigDecimal("126.6249000")));
        var date = LocalDate.of(2026, 10, 15);
        observations.saveAndFlush(observation(station.getId(), date.minusDays(1), BigDecimal.ZERO));
        observations.saveAndFlush(observation(station.getId(), date, new BigDecimal("1.20")));
        observations.saveAndFlush(observation(other.getId(), date, new BigDecimal("2.00")));
        entityManager.clear();

        var found = observations.findByStationIdAndObservationDateBetweenOrderByObservationDateAsc(
                station.getId(), date.minusDays(1), date);
        assertThat(found).hasSize(2);
        assertThat(found.get(0).getPrecipitationMm()).isEqualByComparingTo("0.00");
        assertThat(found.get(1).getPrecipitationMm()).isEqualByComparingTo("1.20");
        assertThat(observations.findByStationIdAndObservationDateBetweenOrderByObservationDateAsc(
                station.getId(), date.plusDays(1), date.plusDays(2))).isEmpty();
    }

    @Test
    void 동일_제공처_지점코드의_중복을_막는다() {
        var station = stations.saveAndFlush(WeatherStationEntity.create(
                "KMA_ASOS", "108", new BigDecimal("37.5714000"), new BigDecimal("126.9658000")));
        assertThat(stations.findBySourceAndStationCode("KMA_ASOS", "108")).isPresent();
        assertThatThrownBy(() -> stations.saveAndFlush(WeatherStationEntity.create(
                "KMA_ASOS", "108", new BigDecimal("37.5714000"), new BigDecimal("126.9658000"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_지점과_날짜의_관측값을_두번_저장할_수_없다() {
        var station = stations.saveAndFlush(WeatherStationEntity.create(
                "KMA_ASOS", "108", new BigDecimal("37.5714000"), new BigDecimal("126.9658000")));
        var date = LocalDate.of(2026, 10, 15);
        observations.saveAndFlush(observation(station.getId(), date, BigDecimal.ZERO));

        assertThatThrownBy(() -> observations.saveAndFlush(
                observation(station.getId(), date, new BigDecimal("1.00"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 강수량이_null이면_결측이고_zero면_무강수로_구분한다() {
        var station = stations.saveAndFlush(WeatherStationEntity.create(
                "KMA_ASOS", "108", new BigDecimal("37.5714000"), new BigDecimal("126.9658000")));
        var date = LocalDate.of(2026, 10, 15);
        observations.saveAndFlush(observation(station.getId(), date, null));
        observations.saveAndFlush(observation(station.getId(), date.plusDays(1), BigDecimal.ZERO));
        entityManager.clear();

        var found = observations.findByStationIdAndObservationDateBetweenOrderByObservationDateAsc(
                station.getId(), date, date.plusDays(1));
        assertThat(found.get(0).getPrecipitationMm()).isNull();
        assertThat(found.get(1).getPrecipitationMm()).isEqualByComparingTo("0.00");
    }

    @Test
    void 매핑되지_않은_기존_매장은_null이고_매핑된_매장은_관측_지점으로_조회된다() {
        var user = users.save(User.create("weather-store@example.com", "encoded", "01099998888",
                LocalDateTime.of(2026, 10, 15, 0, 0)));
        var store = stores.saveAndFlush(Store.create(user, null, null, "맴매", "06236",
                "서울시 강남구", null, LocalDateTime.of(2026, 10, 15, 0, 0)));
        assertThat(store.getWeatherStationId()).isNull();

        var station = stations.saveAndFlush(WeatherStationEntity.create(
                "KMA_ASOS", "108", new BigDecimal("37.5714000"), new BigDecimal("126.9658000")));
        store.updateWeatherStationId(station.getId());
        stores.saveAndFlush(store);
        entityManager.clear();
        assertThat(stores.findById(store.getId())).get()
                .extracting(Store::getWeatherStationId).isEqualTo(station.getId());
    }

    private WeatherObservationEntity observation(Long stationId, LocalDate date, BigDecimal rain) {
        return WeatherObservationEntity.create(stationId, date, new BigDecimal("37.5714000"),
                new BigDecimal("126.9658000"), rain == null ? "UNKNOWN" : rain.signum() == 0 ? "CLEAR" : "RAIN",
                null, rain, "KMA_ASOS");
    }
}
