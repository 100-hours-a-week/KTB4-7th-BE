package com.memme.repository.sales;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import com.memme.entity.sales.SalesAiInsightEntity;
import com.memme.entity.sales.SalesAiInsightStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesAiInsightRepository extends JpaRepository<SalesAiInsightEntity, Long> {

    Optional<SalesAiInsightEntity> findByStoreIdAndTargetMonth(Long storeId, LocalDate targetMonth);

    Page<SalesAiInsightEntity> findAllByTargetMonthBetweenAndStatusOrderByIdAsc(
            LocalDate startMonth, LocalDate endMonth, SalesAiInsightStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SalesAiInsightEntity i "
            + "where i.storeId = :storeId and i.targetMonth = :targetMonth")
    Optional<SalesAiInsightEntity> findByStoreIdAndTargetMonthForUpdate(
            @Param("storeId") Long storeId,
            @Param("targetMonth") LocalDate targetMonth
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SalesAiInsightEntity i where i.id = :id")
    Optional<SalesAiInsightEntity> findByIdForUpdate(@Param("id") Long id);

    default Optional<SalesAiInsightEntity> findByStoreIdAndTargetMonth(
            Long storeId,
            YearMonth targetMonth
    ) {
        return findByStoreIdAndTargetMonth(storeId, targetMonth.atDay(1));
    }
}
