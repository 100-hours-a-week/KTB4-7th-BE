package com.memme.repository.ranking;

import java.time.LocalDate;
import java.util.Optional;

import com.memme.entity.ranking.RankingRecalculationLockEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RankingRecalculationLockRepository extends JpaRepository<RankingRecalculationLockEntity, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO ranking_recalculation_locks (
                period_start, period_end, comparison_start, comparison_end
            ) VALUES (
                :periodStart, :periodEnd, :comparisonStart, :comparisonEnd
            )
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("comparisonStart") LocalDate comparisonStart,
            @Param("comparisonEnd") LocalDate comparisonEnd
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RankingRecalculationLockEntity> findByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEnd(
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate comparisonStart,
            LocalDate comparisonEnd
    );
}
