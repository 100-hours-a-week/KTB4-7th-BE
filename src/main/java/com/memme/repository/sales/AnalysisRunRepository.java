package com.memme.repository.sales;

import java.util.Optional;
import java.util.Collection;
import java.time.LocalDate;
import java.util.List;

import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.AnalysisRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRunEntity, Long> {
    Optional<AnalysisRunEntity> findFirstByBasedOnUploadIdOrderByIdDesc(Long uploadId);

    boolean existsByBasedOnUploadIdAndStatusIn(
            Long uploadId,
            Collection<AnalysisRunStatus> statuses
    );

    Optional<AnalysisRunEntity> findFirstByStoreIdAndStatusOrderByCompletedAtDescIdDesc(
            Long storeId,
            AnalysisRunStatus status
    );

    @Query("""
            select run from AnalysisRunEntity run
            where run.storeId = :storeId
              and run.status = :status
              and run.periodStart <= :periodEnd
              and run.periodEnd >= :periodStart
            """)
    List<AnalysisRunEntity> findAllOverlappingPeriod(
            @Param("storeId") Long storeId,
            @Param("status") AnalysisRunStatus status,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );
}
