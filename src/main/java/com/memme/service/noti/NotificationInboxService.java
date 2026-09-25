package com.memme.service.noti;

import com.memme.dto.noti.NotificationListRequest;
import com.memme.dto.noti.NotificationListResponse;
import com.memme.dto.noti.NotificationReadRequest;
import com.memme.dto.noti.NotificationReadResponse;
import com.memme.dto.noti.NotificationReadStatus;
import com.memme.entity.noti.Notification;
import com.memme.exception.noti.NotificationNotFoundException;
import com.memme.exception.noti.InvalidNotificationCursorException;
import com.memme.repository.noti.NotificationRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationInboxService {

    private static final String LIST_SUCCESS_MESSAGE = "알림을 조회했습니다.";

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationInboxService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long userId, NotificationListRequest request) {
        Cursor cursor = request.cursor() == null ? null : Cursor.decode(request.cursor());
        List<Notification> notifications = notificationRepository.findAllByUserIdOrderBySentAtDesc(userId).stream()
                .filter(notification -> notification.getSentAt() != null)
                .filter(notification -> request.readStatus() != NotificationReadStatus.UNREAD
                        || notification.getReadAt() == null)
                .sorted(Comparator.comparing(Notification::getSentAt, Comparator.reverseOrder())
                        .thenComparing(Notification::getId, Comparator.reverseOrder()))
                .filter(notification -> cursor == null || cursor.isBefore(notification))
                .limit((long) request.size() + 1)
                .toList();

        boolean hasNext = notifications.size() > request.size();
        List<Notification> page = new ArrayList<>(notifications.subList(0, Math.min(notifications.size(), request.size())));
        String nextCursor = hasNext ? Cursor.encode(page.getLast()) : null;

        return new NotificationListResponse(
                LIST_SUCCESS_MESSAGE,
                nextCursor,
                new NotificationListResponse.Data(page.stream().map(this::toItem).toList())
        );
    }

    @Transactional
    public NotificationReadResponse markAsRead(Long userId, NotificationReadRequest request) {
        Set<Long> notificationIds = new LinkedHashSet<>(request.notificationIds());
        Map<Long, Notification> notificationsById = notificationRepository.findAllById(notificationIds).stream()
                .collect(Collectors.toMap(Notification::getId, Function.identity()));
        if (notificationsById.size() != notificationIds.size()
                || notificationsById.values().stream().anyMatch(notification -> notification.getSentAt() == null
                || !userId.equals(notification.getUser().getId()))) {
            throw new NotificationNotFoundException();
        }

        LocalDateTime readAt = LocalDateTime.now(clock);
        int updatedCount = 0;
        for (Notification notification : notificationsById.values()) {
            if (notification.getReadAt() == null) {
                notification.markAsRead(readAt);
                updatedCount++;
            }
        }

        return new NotificationReadResponse(updatedCount, readAt.atZone(clock.getZone()).toOffsetDateTime());
    }

    private NotificationListResponse.Item toItem(Notification notification) {
        return new NotificationListResponse.Item(
                notification.getId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getRelatedEntityType(),
                notification.getRelatedEntityId(),
                notification.getSentAt().atZone(clock.getZone()).toOffsetDateTime(),
                notification.getReadAt() == null ? null : notification.getReadAt().atZone(clock.getZone()).toOffsetDateTime()
        );
    }

    private record Cursor(LocalDateTime sentAt, Long id) {

        private static Cursor decode(String encodedCursor) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(encodedCursor), StandardCharsets.UTF_8);
                String[] values = decoded.split("\\|", -1);
                if (values.length != 2) {
                    throw new IllegalArgumentException();
                }
                return new Cursor(LocalDateTime.parse(values[0]), Long.valueOf(values[1]));
            } catch (IllegalArgumentException exception) {
                throw new InvalidNotificationCursorException();
            }
        }

        private static String encode(Notification notification) {
            String value = notification.getSentAt() + "|" + notification.getId();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        private boolean isBefore(Notification notification) {
            int sentAtComparison = notification.getSentAt().compareTo(sentAt);
            return sentAtComparison < 0 || (sentAtComparison == 0 && notification.getId() < id);
        }
    }
}
