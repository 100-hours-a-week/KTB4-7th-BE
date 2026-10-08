package com.memme.entity.ranking;

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
        name = "ranking_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ranking_profiles_store", columnNames = "store_id"),
                @UniqueConstraint(name = "uk_ranking_profiles_nickname", columnNames = "anonymous_nickname")
        }
)
public class RankingProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "anonymous_nickname", nullable = false, length = 50)
    private String anonymousNickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RankingProfileEntity() {
    }

    private RankingProfileEntity(Long storeId, String anonymousNickname) {
        this.storeId = Objects.requireNonNull(storeId, "storeId");
        this.anonymousNickname = validateNickname(anonymousNickname);
    }

    public static RankingProfileEntity create(Long storeId, String anonymousNickname) {
        return new RankingProfileEntity(storeId, anonymousNickname);
    }

    private static String validateNickname(String anonymousNickname) {
        if (anonymousNickname == null || anonymousNickname.isBlank() || anonymousNickname.length() > 50) {
            throw new IllegalArgumentException("anonymousNickname must be between 1 and 50 characters");
        }
        return anonymousNickname;
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

    public String getAnonymousNickname() {
        return anonymousNickname;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
