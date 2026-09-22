package com.memme.repository.sales;

import java.util.List;

import com.memme.entity.sales.AnalysisMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisMetricRepository extends JpaRepository<AnalysisMetricEntity, Long> {

    List<AnalysisMetricEntity> findAllBySalesAnalysisIdOrderByMetricCodeAsc(Long salesAnalysisId);
}
