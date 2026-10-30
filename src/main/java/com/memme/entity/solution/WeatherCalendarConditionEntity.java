package com.memme.entity.solution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "weather_calendar_conditions", uniqueConstraints = @UniqueConstraint(
        name = "uk_weather_calendar_condition_station_date",
        columnNames = {"station_id", "observation_date"}))
public class WeatherCalendarConditionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    @Column(name = "observation_date", nullable = false)
    private LocalDate observationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "`condition`", nullable = false, length = 10)
    private WeatherCalendarCondition condition;

    @Column(name = "observed_at", nullable = false)
    private LocalDateTime observedAt;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WeatherCalendarConditionEntity() {
    }

    public static WeatherCalendarConditionEntity create(
            Long stationId,
            LocalDate observationDate,
            WeatherCalendarCondition condition,
            LocalDateTime observedAt,
            String source
    ) {
        WeatherCalendarConditionEntity entity = new WeatherCalendarConditionEntity();
        entity.stationId = Objects.requireNonNull(stationId, "stationId");
        entity.observationDate = Objects.requireNonNull(observationDate, "observationDate");
        entity.condition = Objects.requireNonNull(condition, "condition");
        entity.observedAt = Objects.requireNonNull(observedAt, "observedAt");
        if (!observationDate.equals(observedAt.toLocalDate()) || !observedAt.toLocalTime().equals(java.time.LocalTime.NOON)) {
            throw new IllegalArgumentException("observedAt must be local noon on observationDate");
        }
        entity.source = requireText(source, "source");
        return entity;
    }

    @PrePersist
    private void beforeInsert() {
        createdAt = LocalDateTime.now();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getStationId() {
        return stationId;
    }

    public LocalDate getObservationDate() {
        return observationDate;
    }

    public WeatherCalendarCondition getCondition() {
        return condition;
    }

    public LocalDateTime getObservedAt() {
        return observedAt;
    }

    public String getSource() {
        return source;
    }
}
