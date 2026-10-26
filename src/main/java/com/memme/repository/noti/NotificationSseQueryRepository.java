package com.memme.repository.noti;

import com.memme.entity.noti.NotificationType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationSseQueryRepository {

    List<StoredNotification> findSseNotificationsByIdentitiesAndType(
            Collection<NotificationIdentity> identities,
            NotificationType notificationType
    );

    record NotificationIdentity(Long userId, String notificationKey) {
    }

    record StoredNotification(
            Long id,
            Long userId,
            NotificationType notificationType,
            String notificationKey,
            String title,
            String content,
            String relatedEntityType,
            Long relatedEntityId,
            LocalDateTime sentAt,
            LocalDateTime readAt
    ) {
    }
}
