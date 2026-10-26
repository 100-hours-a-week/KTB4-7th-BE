package com.memme.service.noti;

import com.memme.entity.noti.NotificationType;
import com.memme.repository.noti.NotificationRepository;
import com.memme.repository.noti.NotificationSseQueryRepository.NotificationIdentity;
import com.memme.repository.noti.NotificationSseQueryRepository.StoredNotification;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationSseStoredNotificationReader {

    private static final int LOOKUP_SIZE = 100;

    private final NotificationRepository notificationRepository;

    public NotificationSseStoredNotificationReader(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<StoredNotification> find(NotificationType type, List<NotificationIdentity> identities) {
        List<StoredNotification> stored = new ArrayList<>(identities.size());
        for (int start = 0; start < identities.size(); start += LOOKUP_SIZE) {
            List<NotificationIdentity> lookup = identities.subList(start,
                    Math.min(start + LOOKUP_SIZE, identities.size()));
            stored.addAll(notificationRepository.findSseNotificationsByIdentitiesAndType(lookup, type));
        }
        return stored;
    }
}
