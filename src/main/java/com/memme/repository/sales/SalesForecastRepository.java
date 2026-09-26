package com.memme.repository.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import com.memme.entity.sales.SalesForecastEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesForecastRepository extends JpaRepository<SalesForecastEntity, Long> {

    Optional<SalesForecastEntity> findByStoreIdAndTargetDate(Long storeId, LocalDate targetDate);

    List<SalesForecastEntity> findAllByStoreIdAndTargetDateGreaterThanEqualOrderByTargetDateAsc(
            Long storeId,
            LocalDate targetDate
    );

    @Query("""
            select max(forecast.targetDate)
            from SalesForecastEntity forecast
            where forecast.storeId = :storeId
              and forecast.basedOnUploadId = :basedOnUploadId
            """)
    Optional<LocalDate> findForecastEndDateByStoreIdAndBasedOnUploadId(
            @Param("storeId") Long storeId,
            @Param("basedOnUploadId") Long basedOnUploadId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO sales_forecasts (
                store_id, based_on_upload_id, target_date, basis_date,
                predicted_sales_amount, lower_bound, upper_bound, model_version, generated_at
            ) VALUES (
                :storeId, :basedOnUploadId, :targetDate, :basisDate,
                :predictedSalesAmount, :lowerBound, :upperBound, :modelVersion, :generatedAt
            )
            ON DUPLICATE KEY UPDATE
                basis_date = CASE
                    WHEN VALUES(based_on_upload_id) >= based_on_upload_id
                    THEN VALUES(basis_date) ELSE basis_date END,
                predicted_sales_amount = CASE
                    WHEN VALUES(based_on_upload_id) >= based_on_upload_id
                    THEN VALUES(predicted_sales_amount) ELSE predicted_sales_amount END,
                lower_bound = CASE
                    WHEN VALUES(based_on_upload_id) >= based_on_upload_id
                    THEN VALUES(lower_bound) ELSE lower_bound END,
                upper_bound = CASE
                    WHEN VALUES(based_on_upload_id) >= based_on_upload_id
                    THEN VALUES(upper_bound) ELSE upper_bound END,
                model_version = CASE
                    WHEN VALUES(based_on_upload_id) >= based_on_upload_id
                    THEN VALUES(model_version) ELSE model_version END,
                generated_at = CASE
                    WHEN VALUES(based_on_upload_id) >= based_on_upload_id
                    THEN VALUES(generated_at) ELSE generated_at END,
                based_on_upload_id = CASE
                    WHEN VALUES(based_on_upload_id) >= based_on_upload_id
                    THEN VALUES(based_on_upload_id) ELSE based_on_upload_id END
            """, nativeQuery = true)
    int upsertIfLatest(
            @Param("storeId") Long storeId,
            @Param("basedOnUploadId") Long basedOnUploadId,
            @Param("targetDate") LocalDate targetDate,
            @Param("basisDate") LocalDate basisDate,
            @Param("predictedSalesAmount") long predictedSalesAmount,
            @Param("lowerBound") long lowerBound,
            @Param("upperBound") long upperBound,
            @Param("modelVersion") String modelVersion,
            @Param("generatedAt") LocalDateTime generatedAt
    );
}
