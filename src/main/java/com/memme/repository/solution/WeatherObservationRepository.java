package com.memme.repository.solution;

import com.memme.entity.solution.WeatherObservationEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherObservationRepository extends JpaRepository<WeatherObservationEntity, Long> {
    List<WeatherObservationEntity> findByStationIdAndObservationDateBetweenOrderByObservationDateAsc(
            Long stationId, LocalDate from, LocalDate to);
}
