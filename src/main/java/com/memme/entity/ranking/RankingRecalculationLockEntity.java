package com.memme.entity.ranking;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "ranking_recalculation_locks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ranking_recalculation_locks_period",
                columnNames = {"period_start", "period_end", "comparison_start", "comparison_end"}
        )
)
public class RankingRecalculationLockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "comparison_start", nullable = false)
    private LocalDate comparisonStart;

    @Column(name = "comparison_end", nullable = false)
    private LocalDate comparisonEnd;

    protected RankingRecalculationLockEntity() {
    }
}
