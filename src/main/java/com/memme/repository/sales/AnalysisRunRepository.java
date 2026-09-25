package com.memme.repository.sales;

import java.util.Optional;
import java.util.Collection;

import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.AnalysisRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
