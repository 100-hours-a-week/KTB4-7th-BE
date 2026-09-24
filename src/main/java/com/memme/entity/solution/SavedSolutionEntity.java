package com.memme.entity.solution;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "saved_solutions",
        indexes = @Index(name = "idx_saved_solutions_user_created", columnList = "user_id, created_at"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_saved_solutions_user_solution",
                columnNames = {"user_id", "solution_id"}
        )
)
public class SavedSolutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "solution_id", nullable = false)
    private Long solutionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SavedSolutionEntity() {
    }

    private SavedSolutionEntity(Long userId, Long solutionId) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.solutionId = Objects.requireNonNull(solutionId, "solutionId");
    }

    public static SavedSolutionEntity create(Long userId, Long solutionId) {
        return new SavedSolutionEntity(userId, solutionId);
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

    public Long getUserId() {
        return userId;
    }

    public Long getSolutionId() {
        return solutionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
