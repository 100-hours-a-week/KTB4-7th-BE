package com.memme.entity.solution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "weather_forecast_summaries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_weather_forecast_store_date",
                columnNames = {"store_id", "target_date"}
        )
)
public class WeatherForecastSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "basis_datetime", nullable = false)
    private LocalDateTime basisDatetime;

    @Column(name = "sky_text", nullable = false, length = 30)
    private String skyText;

    @Column(name = "min_temp_c", nullable = false)
    private short minTempC;

    @Column(name = "max_temp_c", nullable = false)
    private short maxTempC;

    @Column(name = "max_pop", nullable = false, precision = 4, scale = 3)
    private BigDecimal maxPop;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rain_hours", nullable = false, columnDefinition = "json")
    private List<Integer> rainHours;

    @Column(name = "precipitation_mm", precision = 8, scale = 2)
    private BigDecimal precipitationMm;

    @Column(name = "is_notable", nullable = false)
    private boolean notable;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WeatherForecastSummaryEntity() {
    }

    private WeatherForecastSummaryEntity(
            Long storeId,
            LocalDate targetDate,
            LocalDateTime basisDatetime,
            String skyText,
            int minTempC,
            int maxTempC,
            BigDecimal maxPop,
            List<Integer> rainHours,
            BigDecimal precipitationMm,
            boolean notable,
            LocalDateTime fetchedAt
    ) {
        this.storeId = Objects.requireNonNull(storeId, "storeId");
        this.targetDate = Objects.requireNonNull(targetDate, "targetDate");
        this.basisDatetime = Objects.requireNonNull(basisDatetime, "basisDatetime");
        this.skyText = Objects.requireNonNull(skyText, "skyText");
        if (minTempC < Short.MIN_VALUE || minTempC > Short.MAX_VALUE
                || maxTempC < Short.MIN_VALUE || maxTempC > Short.MAX_VALUE) {
            throw new IllegalArgumentException("temperature is out of range");
        }
        this.minTempC = (short) minTempC;
        this.maxTempC = (short) maxTempC;
        if (maxPop == null || maxPop.compareTo(BigDecimal.ZERO) < 0
                || maxPop.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("maxPop must be between 0 and 1");
        }
        this.maxPop = maxPop;
        this.rainHours = List.copyOf(Objects.requireNonNull(rainHours, "rainHours"));
        if (this.rainHours.stream().anyMatch(hour -> hour < 0 || hour > 23)) {
            throw new IllegalArgumentException("rainHours must be between 0 and 23");
        }
        if (precipitationMm != null && precipitationMm.signum() < 0) {
            throw new IllegalArgumentException("precipitationMm must not be negative");
        }
        this.precipitationMm = precipitationMm;
        this.notable = notable;
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "fetchedAt");
    }

    public static WeatherForecastSummaryEntity create(
            Long storeId,
            LocalDate targetDate,
            LocalDateTime basisDatetime,
            String skyText,
            int minTempC,
            int maxTempC,
            BigDecimal maxPop,
            List<Integer> rainHours,
            BigDecimal precipitationMm,
            boolean notable,
            LocalDateTime fetchedAt
    ) {
        return new WeatherForecastSummaryEntity(storeId, targetDate, basisDatetime, skyText,
                minTempC, maxTempC, maxPop, rainHours, precipitationMm, notable, fetchedAt);
    }

    @PrePersist
    private void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public LocalDate getTargetDate() { return targetDate; }
    public LocalDateTime getBasisDatetime() { return basisDatetime; }
    public String getSkyText() { return skyText; }
    public short getMinTempC() { return minTempC; }
    public short getMaxTempC() { return maxTempC; }
    public BigDecimal getMaxPop() { return maxPop; }
    public List<Integer> getRainHours() { return List.copyOf(rainHours); }
    public BigDecimal getPrecipitationMm() { return precipitationMm; }
    public boolean isNotable() { return notable; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
