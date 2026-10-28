package com.memme.repository.solution;

import com.memme.entity.solution.WeatherCalendarConditionEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherCalendarConditionRepository
        extends JpaRepository<WeatherCalendarConditionEntity, Long> {

    List<WeatherCalendarConditionEntity> findAllByStationIdAndObservationDateBetweenOrderByObservationDateAsc(
            Long stationId,
            LocalDate from,
            LocalDate to
    );
}
