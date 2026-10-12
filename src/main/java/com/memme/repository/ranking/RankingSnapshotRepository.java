package com.memme.repository.ranking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import com.memme.entity.ranking.RankingSnapshotEntity;
import com.memme.entity.ranking.RankingSnapshotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshotEntity, Long> {

    @Query("""
            select snapshot from RankingSnapshotEntity snapshot
            where snapshot.periodStart = :periodStart
              and snapshot.periodEnd = :periodEnd
              and snapshot.comparisonStart = :comparisonStart
              and snapshot.comparisonEnd = :comparisonEnd
              and snapshot.status = com.memme.entity.ranking.RankingSnapshotStatus.COMPLETED
              and snapshot.calculatedAt >= :dayStart
              and snapshot.calculatedAt < :nextDayStart
            order by snapshot.calculatedAt desc, snapshot.revisionNo desc
            limit 1
            """)
    Optional<RankingSnapshotEntity> findLatestCompletedForDay(
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("comparisonStart") LocalDate comparisonStart,
            @Param("comparisonEnd") LocalDate comparisonEnd,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("nextDayStart") LocalDateTime nextDayStart
    );

    Optional<RankingSnapshotEntity> findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndOrderByRevisionNoDesc(
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate comparisonStart,
            LocalDate comparisonEnd
    );

    Optional<RankingSnapshotEntity> findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndAndStatusOrderByRevisionNoDesc(
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate comparisonStart,
            LocalDate comparisonEnd,
            RankingSnapshotStatus status
    );
}
