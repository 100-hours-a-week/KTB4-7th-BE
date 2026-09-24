package com.memme.entity.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "store_business_hours",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_store_business_hours_store_day",
                columnNames = {"store_id", "day_of_week"}
        )
)
public class StoreBusinessHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "opens_at")
    private LocalTime opensAt;

    @Column(name = "closes_at")
    private LocalTime closesAt;

    @Column(name = "is_closed", nullable = false)
    private Boolean closed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected StoreBusinessHours() {
    }

    public static StoreBusinessHours create(
            Store store,
            Integer dayOfWeek,
            LocalTime opensAt,
            LocalTime closesAt,
            boolean closed,
            LocalDateTime createdAt
    ) {
        StoreBusinessHours businessHours = new StoreBusinessHours();
        businessHours.store = store;
        businessHours.dayOfWeek = dayOfWeek;
        businessHours.opensAt = opensAt;
        businessHours.closesAt = closesAt;
        businessHours.closed = closed;
        businessHours.createdAt = createdAt;
        businessHours.updatedAt = createdAt;
        return businessHours;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpensAt() {
        return opensAt;
    }

    public LocalTime getClosesAt() {
        return closesAt;
    }

    public Boolean isClosed() {
        return closed;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateBusinessHours(
            LocalTime opensAt,
            LocalTime closesAt,
            Boolean closed,
            LocalDateTime updatedAt
    ) {
        this.closed = closed;
        this.opensAt = closed ? null : opensAt;
        this.closesAt = closed ? null : closesAt;
        this.updatedAt = updatedAt;
    }
}
