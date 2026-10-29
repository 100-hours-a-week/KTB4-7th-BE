package com.memme.repository.noti;

import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByUserIdOrderBySentAtDesc(Long userId);

    boolean existsByUserIdAndNotificationTypeAndNotificationKey(
            Long userId,
            NotificationType notificationType,
            String notificationKey
    );

    List<Notification> findAllByUserIdInAndNotificationTypeAndNotificationKeyIn(
            Collection<Long> userIds, NotificationType notificationType, Collection<String> notificationKeys
    );

    @Query("""
            select notification.user.id as userId,
                   notification.notificationKey as notificationKey
            from Notification notification
            where notification.user.id in :userIds
              and notification.notificationType = :notificationType
              and notification.notificationKey in :notificationKeys
            """)
    List<NotificationUserKey> findNotificationUserKeysByUserIdsAndTypeAndKeysIn(
            @Param("userIds") Collection<Long> userIds,
            @Param("notificationType") NotificationType notificationType,
            @Param("notificationKeys") Collection<String> notificationKeys
    );

    interface NotificationUserKey {

        Long getUserId();

        String getNotificationKey();
    }
}
