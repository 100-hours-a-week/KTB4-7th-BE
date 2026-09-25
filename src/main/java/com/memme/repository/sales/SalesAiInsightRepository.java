package com.memme.repository.sales;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import com.memme.entity.sales.SalesAiInsightEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesAiInsightRepository extends JpaRepository<SalesAiInsightEntity, Long> {

    Optional<SalesAiInsightEntity> findByStoreIdAndTargetMonth(Long storeId, LocalDate targetMonth);

    default Optional<SalesAiInsightEntity> findByStoreIdAndTargetMonth(
            Long storeId,
            YearMonth targetMonth
    ) {
        return findByStoreIdAndTargetMonth(storeId, targetMonth.atDay(1));
    }
}
