package com.memme.service.noti;

import com.memme.dto.noti.NotificationListResponse;
import com.memme.dto.noti.NotificationSseEvent;
import com.memme.entity.noti.NotificationType;
import com.memme.repository.noti.NotificationSseQueryRepository.NotificationIdentity;
import com.memme.repository.noti.NotificationSseQueryRepository.StoredNotification;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NotificationSseDeliveryService {

    private final NotificationSseStoredNotificationReader reader;
    private final NotificationSseConnectionService connectionService;
    private final Clock clock;

    public NotificationSseDeliveryService(
            NotificationSseStoredNotificationReader reader,
            NotificationSseConnectionService connectionService,
            Clock clock
    ) {
        this.reader = reader;
        this.connectionService = connectionService;
        this.clock = clock;
    }

    public void deliver(NotificationType type, List<NotificationIdentity> identities) {
        for (StoredNotification stored : reader.find(type, identities)) {
            if (stored.sentAt() == null) {
                continue;
            }
            NotificationListResponse.Item item = new NotificationListResponse.Item(
                    stored.id(),
                    stored.notificationType(),
                    stored.title(),
                    stored.content(),
                    stored.relatedEntityType(),
                    stored.relatedEntityId(),
                    stored.sentAt().atZone(clock.getZone()).toOffsetDateTime(),
                    stored.readAt() == null ? null
                            : stored.readAt().atZone(clock.getZone()).toOffsetDateTime()
            );
            connectionService.sendToUser(stored.userId(), new NotificationSseEvent(item));
        }
    }
}
