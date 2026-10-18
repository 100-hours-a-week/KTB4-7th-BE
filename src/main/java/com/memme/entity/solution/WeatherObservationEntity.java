package com.memme.entity.solution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "weather_observations", uniqueConstraints = @UniqueConstraint(
        name = "uk_weather_observation_station_date", columnNames = {"station_id", "observation_date"}))
public class WeatherObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "observation_date", nullable = false)
    private LocalDate observationDate;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "weather_code", nullable = false, length = 30)
    private String weatherCode;

    @Column(name = "temperature_celsius", precision = 5, scale = 2)
    private BigDecimal temperatureCelsius;

    @Column(name = "precipitation_mm", precision = 8, scale = 2)
    private BigDecimal precipitationMm;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WeatherObservationEntity() {}

    public static WeatherObservationEntity create(Long stationId, LocalDate observationDate,
                                                   BigDecimal latitude, BigDecimal longitude,
                                                   String weatherCode, BigDecimal temperatureCelsius,
                                                   BigDecimal precipitationMm, String source) {
        if (precipitationMm != null && precipitationMm.signum() < 0) {
            throw new IllegalArgumentException("precipitationMm must not be negative");
        }
        WeatherObservationEntity observation = new WeatherObservationEntity();
        observation.stationId = Objects.requireNonNull(stationId, "stationId");
        observation.observationDate = Objects.requireNonNull(observationDate, "observationDate");
        observation.latitude = Objects.requireNonNull(latitude, "latitude");
        observation.longitude = Objects.requireNonNull(longitude, "longitude");
        observation.weatherCode = Objects.requireNonNull(weatherCode, "weatherCode");
        observation.temperatureCelsius = temperatureCelsius;
        observation.precipitationMm = precipitationMm;
        observation.source = Objects.requireNonNull(source, "source");
        return observation;
    }

    @PrePersist
    private void beforeInsert() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getStationId() { return stationId; }
    public LocalDate getObservationDate() { return observationDate; }
    public BigDecimal getPrecipitationMm() { return precipitationMm; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
