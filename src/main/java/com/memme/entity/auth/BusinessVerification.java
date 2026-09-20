package com.memme.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "business_verifications",
        indexes = @Index(name = "idx_business_verifications_expires_at", columnList = "expires_at")
)
public class BusinessVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "business_reg_number", nullable = false, length = 10, columnDefinition = "CHAR(10)")
    private String businessRegNumber;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected BusinessVerification() {
    }

    public static BusinessVerification create(
            String businessRegNumber,
            LocalDateTime verifiedAt,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
        BusinessVerification businessVerification = new BusinessVerification();
        businessVerification.businessRegNumber = businessRegNumber;
        businessVerification.verifiedAt = verifiedAt;
        businessVerification.expiresAt = expiresAt;
        businessVerification.createdAt = createdAt;
        return businessVerification;
    }

    public Long getId() {
        return id;
    }
}
