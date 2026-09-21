package com.memme.repository.sales;

import java.util.Optional;

import com.memme.entity.sales.SalesAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesAnalysisRepository extends JpaRepository<SalesAnalysisEntity, Long> {

    Optional<SalesAnalysisEntity> findByAnalysisRunId(Long analysisRunId);
}
