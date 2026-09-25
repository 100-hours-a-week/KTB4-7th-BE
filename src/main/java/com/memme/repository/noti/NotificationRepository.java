package com.memme.repository.noti;

import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByUserIdOrderBySentAtDesc(Long userId);

    boolean existsByUserIdAndNotificationTypeAndNotificationKey(
            Long userId,
            NotificationType notificationType,
            String notificationKey
    );
}
