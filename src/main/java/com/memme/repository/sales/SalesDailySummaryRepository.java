package com.memme.repository.sales;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.memme.entity.sales.SalesDailySummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesDailySummaryRepository extends JpaRepository<SalesDailySummaryEntity, Long> {

    Optional<SalesDailySummaryEntity> findByStoreIdAndSalesDate(Long storeId, LocalDate salesDate);

    List<SalesDailySummaryEntity> findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
            Long storeId,
            LocalDate periodStart,
            LocalDate periodEnd
    );
}
