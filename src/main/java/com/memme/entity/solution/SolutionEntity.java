package com.memme.entity.solution;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "solutions",
        indexes = @Index(
                name = "idx_solutions_store_created",
                columnList = "store_id, created_at"
        ),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_solutions_bundle_rank",
                columnNames = {"solution_bundle_id", "rank_no"}
        )
)
public class SolutionEntity {

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int DETAIL_MAX_LENGTH = 1_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "sales_analysis_id", nullable = false)
    private Long salesAnalysisId;

    @Column(name = "solution_bundle_id", nullable = false)
    private Long solutionBundleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "solution_type", nullable = false, length = 30)
    private SolutionType solutionType;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "detail_text", nullable = false, columnDefinition = "TEXT")
    private String detailText;

    @Column(name = "evidence_text", columnDefinition = "TEXT")
    private String evidenceText;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    protected SolutionEntity() {
    }

    private SolutionEntity(
            Long storeId,
            Long salesAnalysisId,
            Long solutionBundleId,
            SolutionType solutionType,
            String title,
            String summaryText,
            String detailText,
            String evidenceText,
            LocalDateTime generatedAt,
            int rankNo
    ) {
        this.storeId = Objects.requireNonNull(storeId, "storeId");
        this.salesAnalysisId = Objects.requireNonNull(salesAnalysisId, "salesAnalysisId");
        this.solutionBundleId = Objects.requireNonNull(solutionBundleId, "solutionBundleId");
        this.solutionType = Objects.requireNonNull(solutionType, "solutionType");
        this.title = requireText(title, "title", TITLE_MAX_LENGTH);
        this.summaryText = requireText(summaryText, "summaryText");
        this.detailText = requireText(detailText, "detailText", DETAIL_MAX_LENGTH);
        this.evidenceText = evidenceText;
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        if (rankNo <= 0) {
            throw new IllegalArgumentException("rankNo must be positive");
        }
        this.rankNo = rankNo;
    }

    public static SolutionEntity create(
            Long storeId,
            Long salesAnalysisId,
            Long solutionBundleId,
            SolutionType solutionType,
            String title,
            String summaryText,
            String detailText,
            String evidenceText,
            LocalDateTime generatedAt,
            int rankNo
    ) {
        return new SolutionEntity(
                storeId,
                salesAnalysisId,
                solutionBundleId,
                solutionType,
                title,
                summaryText,
                detailText,
                evidenceText,
                generatedAt,
                rankNo
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        String validated = requireText(value, fieldName);
        if (validated.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + maxLength + " characters"
            );
        }
        return validated;
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

    public Long getStoreId() {
        return storeId;
    }

    public Long getSalesAnalysisId() {
        return salesAnalysisId;
    }

    public Long getSolutionBundleId() {
        return solutionBundleId;
    }

    public SolutionType getSolutionType() {
        return solutionType;
    }

    public String getTitle() {
        return title;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public String getDetailText() {
        return detailText;
    }

    public String getEvidenceText() {
        return evidenceText;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public int getRankNo() {
        return rankNo;
    }
}
