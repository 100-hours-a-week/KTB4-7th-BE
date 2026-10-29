package com.memme.service.noti;

import com.memme.dto.noti.FcmPushMessage;
import com.memme.dto.noti.NotificationListResponse;
import com.memme.entity.noti.FcmDelivery;
import com.memme.entity.noti.FcmToken;
import com.memme.entity.noti.Notification;
import com.memme.service.noti.fcm.FcmClient;
import com.memme.service.noti.fcm.FcmSendResult;
import com.memme.repository.noti.FcmDeliveryRepository;
import com.memme.repository.noti.FcmTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FcmDeliveryAttemptService {

    private static final int MAX_ATTEMPTS = 3;
    static final Set<String> RETRYABLE_ERRORS = Set.of(
            "UNAVAILABLE", "INTERNAL", "QUOTA_EXCEEDED", "FCM_CLIENT_ERROR");

    private final FcmDeliveryRepository deliveryRepository;
    private final FcmTokenRepository tokenRepository;
    private final FcmClient fcmClient;
    private final Clock clock;

    public FcmDeliveryAttemptService(
            FcmDeliveryRepository deliveryRepository,
            FcmTokenRepository tokenRepository,
            FcmClient fcmClient,
            Clock clock
    ) {
        this.deliveryRepository = deliveryRepository;
        this.tokenRepository = tokenRepository;
        this.fcmClient = fcmClient;
        this.clock = clock;
    }

    @Transactional
    public void attempt(Long deliveryId) {
        FcmDelivery delivery = deliveryRepository.findByIdForUpdate(deliveryId).orElse(null);
        if (delivery == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!isReady(delivery, now)) {
            return;
        }
        FcmToken token = delivery.getFcmToken();
        if (token == null || !delivery.getTokenHash().equals(token.getTokenHash())) {
            delivery.markFailed("TOKEN_MISSING", now);
            return;
        }

        Notification notification = delivery.getNotification();
        NotificationListResponse.Item item = new NotificationListResponse.Item(
                notification.getId(), notification.getNotificationType(), notification.getTitle(),
                notification.getContent(), notification.getRelatedEntityType(), notification.getRelatedEntityId(),
                notification.getSentAt().atZone(clock.getZone()).toOffsetDateTime(), null
        );
        delivery.markAttemptStarted(now);
        FcmSendResult result;
        try {
            result = fcmClient.send(FcmPushMessage.from(token.getTokenValue(), item));
        } catch (RuntimeException exception) {
            delivery.markFailed("FCM_CLIENT_ERROR", LocalDateTime.now(clock));
            return;
        }

        LocalDateTime completedAt = LocalDateTime.now(clock);
        switch (result.status()) {
            case SENT -> delivery.markSent(completedAt);
            case INVALID_TOKEN -> {
                delivery.markFailed(result.errorCode(), completedAt);
                tokenRepository.delete(token);
            }
            case RETRYABLE_FAILURE, PERMANENT_FAILURE -> delivery.markFailed(result.errorCode(), completedAt);
            case DISABLED -> delivery.markFailed("FCM_DISABLED", completedAt);
        }
    }

    static boolean isReady(FcmDelivery delivery, LocalDateTime now) {
        if (delivery.getStatus() == FcmDelivery.Status.SENT || delivery.getAttemptCount() >= MAX_ATTEMPTS) {
            return false;
        }
        if (delivery.getStatus() == FcmDelivery.Status.FAILED
                && (delivery.getLastErrorCode() == null
                || !RETRYABLE_ERRORS.contains(delivery.getLastErrorCode()))) {
            return false;
        }
        if (delivery.getAttemptCount() == 0) {
            return true;
        }
        Duration delay = delivery.getAttemptCount() == 1 ? Duration.ofMinutes(1) : Duration.ofMinutes(5);
        return delivery.getLastAttemptAt() != null
                && !delivery.getLastAttemptAt().plus(delay).isAfter(now);
    }
}
