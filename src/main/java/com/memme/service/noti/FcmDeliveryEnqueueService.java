package com.memme.service.noti;

import com.memme.entity.noti.FcmDelivery;
import com.memme.entity.noti.FcmToken;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationType;
import com.memme.repository.noti.FcmDeliveryRepository;
import com.memme.repository.noti.FcmTokenRepository;
import com.memme.repository.noti.NotificationBulkInsertRepository.NotificationRow;
import com.memme.repository.noti.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FcmDeliveryEnqueueService {

    private static final int LOOKUP_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final FcmTokenRepository tokenRepository;
    private final FcmDeliveryRepository deliveryRepository;
    private final Clock clock;

    public FcmDeliveryEnqueueService(
            NotificationRepository notificationRepository,
            FcmTokenRepository tokenRepository,
            FcmDeliveryRepository deliveryRepository,
            Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.tokenRepository = tokenRepository;
        this.deliveryRepository = deliveryRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueSaved(List<Notification> notifications) {
        for (int start = 0; start < notifications.size(); start += LOOKUP_SIZE) {
            enqueueChunk(notifications.subList(start, Math.min(start + LOOKUP_SIZE, notifications.size())));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueBulkRows(List<NotificationRow> rows) {
        for (int start = 0; start < rows.size(); start += LOOKUP_SIZE) {
            List<NotificationRow> part = rows.subList(start, Math.min(start + LOOKUP_SIZE, rows.size()));
            List<Long> userIds = part.stream().map(NotificationRow::userId).distinct().toList();
            List<String> keys = part.stream().map(NotificationRow::notificationKey).distinct().toList();
            Set<UserKey> expected = new HashSet<>();
            for (NotificationRow row : part) {
                expected.add(new UserKey(row.userId(), row.notificationKey()));
            }
            List<Notification> notifications = notificationRepository
                    .findAllByUserIdInAndNotificationTypeAndNotificationKeyIn(
                            userIds, NotificationType.SOLUTION_READY, keys)
                    .stream()
                    .filter(notification -> expected.contains(new UserKey(
                            notification.getUser().getId(), notification.getNotificationKey())))
                    .toList();
            if (notifications.size() != expected.size()) {
                throw new IllegalStateException("저장된 솔루션 알림을 발송 대상으로 찾지 못했습니다.");
            }
            enqueueChunk(notifications);
        }
    }

    private void enqueueChunk(List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        List<Long> userIds = notifications.stream().map(notification -> notification.getUser().getId())
                .distinct().toList();
        Map<Long, List<FcmToken>> tokensByUser = new HashMap<>();
        for (FcmToken token : tokenRepository.findAllByUserIdIn(userIds)) {
            tokensByUser.computeIfAbsent(token.getUser().getId(), ignored -> new ArrayList<>()).add(token);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<FcmDelivery> deliveries = new ArrayList<>();
        for (Notification notification : notifications) {
            for (FcmToken token : tokensByUser.getOrDefault(notification.getUser().getId(), List.of())) {
                deliveries.add(FcmDelivery.pending(notification, token, token.getTokenHash(), now));
            }
        }
        deliveryRepository.saveAll(deliveries);
    }

    private record UserKey(Long userId, String notificationKey) {
    }
}
