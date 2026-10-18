package com.memme.repository.solution;

import com.memme.entity.solution.WeatherStationEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherStationRepository extends JpaRepository<WeatherStationEntity, Long> {
    Optional<WeatherStationEntity> findBySourceAndStationCode(String source, String stationCode);
}
