package com.memme.repository.sales;

import java.util.Optional;

import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.AnalysisRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRunEntity, Long> {
    Optional<AnalysisRunEntity> findByBasedOnUploadId(Long uploadId);

    Optional<AnalysisRunEntity> findFirstByStoreIdAndStatusOrderByCompletedAtDescIdDesc(
            Long storeId,
            AnalysisRunStatus status
    );
}
