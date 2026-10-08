package com.memme.entity.ranking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "ranking_entries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ranking_entries_snapshot_profile",
                columnNames = {"ranking_snapshot_id", "ranking_profile_id"}
        )
)
public class RankingEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ranking_snapshot_id", nullable = false)
    private Long rankingSnapshotId;

    @Column(name = "ranking_profile_id", nullable = false)
    private Long rankingProfileId;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(name = "growth_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal growthRate;

    @Column(name = "selected_period_sales", nullable = false)
    private long selectedPeriodSales;

    @Column(name = "comparison_period_sales", nullable = false)
    private long comparisonPeriodSales;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RankingEntryEntity() {
    }

    private RankingEntryEntity(
            Long rankingSnapshotId,
            Long rankingProfileId,
            int rankNo,
            BigDecimal growthRate,
            long selectedPeriodSales,
            long comparisonPeriodSales
    ) {
        this.rankingSnapshotId = Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        this.rankingProfileId = Objects.requireNonNull(rankingProfileId, "rankingProfileId");
        if (rankNo < 1) {
            throw new IllegalArgumentException("rankNo must be positive");
        }
        if (selectedPeriodSales < 0 || comparisonPeriodSales <= 0) {
            throw new IllegalArgumentException("sales amounts must be non-negative and comparison sales positive");
        }
        this.rankNo = rankNo;
        this.growthRate = Objects.requireNonNull(growthRate, "growthRate");
        this.selectedPeriodSales = selectedPeriodSales;
        this.comparisonPeriodSales = comparisonPeriodSales;
    }

    public static RankingEntryEntity create(
            Long rankingSnapshotId,
            Long rankingProfileId,
            int rankNo,
            BigDecimal growthRate,
            long selectedPeriodSales,
            long comparisonPeriodSales
    ) {
        return new RankingEntryEntity(
                rankingSnapshotId,
                rankingProfileId,
                rankNo,
                growthRate,
                selectedPeriodSales,
                comparisonPeriodSales
        );
    }

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getRankingSnapshotId() {
        return rankingSnapshotId;
    }

    public Long getRankingProfileId() {
        return rankingProfileId;
    }

    public int getRankNo() {
        return rankNo;
    }

    public BigDecimal getGrowthRate() {
        return growthRate;
    }

    public long getSelectedPeriodSales() {
        return selectedPeriodSales;
    }

    public long getComparisonPeriodSales() {
        return comparisonPeriodSales;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
