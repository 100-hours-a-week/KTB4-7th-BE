package com.memme.entity.solution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "weather_stations", uniqueConstraints = @UniqueConstraint(
        name = "uk_weather_station_source_code", columnNames = {"source", "station_code"}))
public class WeatherStationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "station_code", nullable = false, length = 50)
    private String stationCode;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    protected WeatherStationEntity() {}

    public static WeatherStationEntity create(String source, String stationCode,
                                              BigDecimal latitude, BigDecimal longitude) {
        WeatherStationEntity station = new WeatherStationEntity();
        station.source = Objects.requireNonNull(source, "source");
        station.stationCode = Objects.requireNonNull(stationCode, "stationCode");
        station.latitude = coordinate(latitude, new BigDecimal("-90"), new BigDecimal("90"), "latitude");
        station.longitude = coordinate(longitude, new BigDecimal("-180"), new BigDecimal("180"), "longitude");
        return station;
    }

    private static BigDecimal coordinate(BigDecimal value, BigDecimal min, BigDecimal max, String name) {
        Objects.requireNonNull(value, name);
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(name + " is out of range");
        }
        return value;
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public String getStationCode() { return stationCode; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
}
