package com.memme.repository.noti;

import com.memme.entity.noti.FcmDelivery;
import com.memme.entity.noti.FcmDelivery.Status;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FcmDeliveryRepository extends JpaRepository<FcmDelivery, Long> {

    Optional<FcmDelivery> findByNotificationIdAndTokenHash(Long notificationId, String tokenHash);

    List<FcmDelivery> findAllByNotificationIdOrderByIdAsc(Long notificationId);

    List<FcmDelivery> findAllByStatusOrderByCreatedAtAsc(Status status);
}
