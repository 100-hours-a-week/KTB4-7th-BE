package com.memme.repository.sales;

import java.util.List;

import com.memme.entity.sales.MenuAnalysisResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuAnalysisResultRepository
        extends JpaRepository<MenuAnalysisResultEntity, Long> {

    List<MenuAnalysisResultEntity> findAllBySalesAnalysisIdOrderBySalesRankAscIdAsc(
            Long salesAnalysisId
    );
}
