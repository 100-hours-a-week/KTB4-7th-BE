package com.memme.repository.noti;

import com.memme.entity.noti.FcmDelivery;
import com.memme.entity.noti.FcmDelivery.Status;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FcmDeliveryRepository extends JpaRepository<FcmDelivery, Long> {

    Optional<FcmDelivery> findByNotificationIdAndTokenHash(Long notificationId, String tokenHash);

    List<FcmDelivery> findAllByNotificationIdOrderByIdAsc(Long notificationId);

    List<FcmDelivery> findAllByStatusOrderByCreatedAtAsc(Status status);

    @Query("""
            select delivery from FcmDelivery delivery
            where (delivery.status = :pending and delivery.attemptCount = 0)
               or (delivery.status in :activeStatuses
                   and (delivery.status = :pending or delivery.lastErrorCode in :retryableErrors)
                   and ((delivery.attemptCount = 1 and delivery.lastAttemptAt <= :firstRetryAt)
                     or (delivery.attemptCount = 2 and delivery.lastAttemptAt <= :secondRetryAt)))
            order by delivery.createdAt asc, delivery.id asc
            """)
    List<FcmDelivery> findReadyToAttempt(
            @Param("pending") Status pending,
            @Param("activeStatuses") List<Status> activeStatuses,
            @Param("retryableErrors") List<String> retryableErrors,
            @Param("firstRetryAt") LocalDateTime firstRetryAt,
            @Param("secondRetryAt") LocalDateTime secondRetryAt,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select delivery from FcmDelivery delivery where delivery.id = :id")
    Optional<FcmDelivery> findByIdForUpdate(@Param("id") Long id);
}
