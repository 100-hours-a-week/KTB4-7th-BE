package com.memme.repository.ranking;

import java.time.LocalDate;
import java.util.Optional;

import com.memme.entity.ranking.RankingSnapshotEntity;
import com.memme.entity.ranking.RankingSnapshotStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshotEntity, Long> {

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
