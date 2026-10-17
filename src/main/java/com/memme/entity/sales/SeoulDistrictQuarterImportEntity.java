package com.memme.entity.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "seoul_district_quarter_imports", uniqueConstraints = @UniqueConstraint(
        name = "uk_seoul_district_import_quarter_industry", columnNames = {"basis_quarter", "industry_code"}))
public class SeoulDistrictQuarterImportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "basis_quarter", nullable = false, length = 6, columnDefinition = "CHAR(6)")
    private String basisQuarter;

    @Column(name = "industry_code", nullable = false, length = 8, columnDefinition = "CHAR(8)")
    private String industryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeoulDistrictQuarterImportStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SeoulDistrictQuarterImportEntity() {}

    public static SeoulDistrictQuarterImportEntity start(String basisQuarter, String industryCode,
                                                         LocalDateTime now) {
        if (basisQuarter == null || !basisQuarter.matches("[0-9]{4}Q[1-4]")) {
            throw new IllegalArgumentException("basisQuarter must be YYYYQ1..YYYYQ4");
        }
        if (!"CS100010".equals(industryCode)) {
            throw new IllegalArgumentException("industryCode must be CS100010");
        }
        SeoulDistrictQuarterImportEntity imported = new SeoulDistrictQuarterImportEntity();
        imported.basisQuarter = basisQuarter;
        imported.industryCode = industryCode;
        imported.status = SeoulDistrictQuarterImportStatus.PROCESSING;
        imported.startedAt = Objects.requireNonNull(now, "now");
        imported.updatedAt = now;
        return imported;
    }

    public void restart(LocalDateTime now) {
        startedAt = Objects.requireNonNull(now, "now");
        updatedAt = now;
        completedAt = null;
        status = SeoulDistrictQuarterImportStatus.PROCESSING;
    }

    public void complete(LocalDateTime now) {
        completedAt = Objects.requireNonNull(now, "now");
        updatedAt = now;
        status = SeoulDistrictQuarterImportStatus.COMPLETED;
    }

    public void fail(LocalDateTime now) {
        updatedAt = Objects.requireNonNull(now, "now");
        completedAt = null;
        status = SeoulDistrictQuarterImportStatus.FAILED;
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (status != SeoulDistrictQuarterImportStatus.COMPLETED && completedAt != null) {
            throw new IllegalStateException("completedAt requires COMPLETED status");
        }
    }

    public Long getId() { return id; }
    public String getBasisQuarter() { return basisQuarter; }
    public String getIndustryCode() { return industryCode; }
    public SeoulDistrictQuarterImportStatus getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
