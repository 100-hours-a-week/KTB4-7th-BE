package com.memme.repository.solution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.memme.entity.solution.WeatherForecastSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WeatherForecastSummaryRepository extends JpaRepository<WeatherForecastSummaryEntity, Long> {

    Optional<WeatherForecastSummaryEntity> findByStoreIdAndTargetDate(Long storeId, LocalDate targetDate);

    default int updateIfNewer(WeatherForecastSummaryEntity incoming) {
        return updateIfNewerFields(incoming.getStoreId(), incoming.getTargetDate(),
                incoming.getBasisDatetime(), incoming.getSkyText(), incoming.getMinTempC(),
                incoming.getMaxTempC(), incoming.getMaxPop(), incoming.getRainHours(),
                incoming.getPrecipitationMm(), incoming.isNotable(), incoming.getFetchedAt());
    }

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE WeatherForecastSummaryEntity summary
            SET summary.basisDatetime = :basisDatetime,
                summary.skyText = :skyText,
                summary.minTempC = :minTempC,
                summary.maxTempC = :maxTempC,
                summary.maxPop = :maxPop,
                summary.rainHours = :rainHours,
                summary.precipitationMm = :precipitationMm,
                summary.notable = :notable,
                summary.fetchedAt = :fetchedAt,
                summary.updatedAt = CURRENT_TIMESTAMP
            WHERE summary.storeId = :storeId
              AND summary.targetDate = :targetDate
              AND summary.basisDatetime < :basisDatetime
            """)
    int updateIfNewerFields(
            @Param("storeId") Long storeId,
            @Param("targetDate") LocalDate targetDate,
            @Param("basisDatetime") LocalDateTime basisDatetime,
            @Param("skyText") String skyText,
            @Param("minTempC") short minTempC,
            @Param("maxTempC") short maxTempC,
            @Param("maxPop") BigDecimal maxPop,
            @Param("rainHours") List<Integer> rainHours,
            @Param("precipitationMm") BigDecimal precipitationMm,
            @Param("notable") boolean notable,
            @Param("fetchedAt") LocalDateTime fetchedAt
    );
}
