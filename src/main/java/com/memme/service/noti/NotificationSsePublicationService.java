package com.memme.service.noti;

import com.memme.entity.noti.NotificationType;
import com.memme.repository.noti.NotificationSseQueryRepository.NotificationIdentity;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationSsePublicationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSsePublicationService.class);

    private final NotificationSseDeliveryService deliveryService;

    public NotificationSsePublicationService(NotificationSseDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    public void publishAfterCommit(NotificationType type, Collection<NotificationIdentity> identities) {
        Objects.requireNonNull(type, "type");
        List<NotificationIdentity> notifications = List.copyOf(identities);
        if (notifications.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("SSE 알림 발행은 활성 트랜잭션 안에서 예약해야 합니다.");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    deliveryService.deliver(type, notifications);
                } catch (RuntimeException exception) {
                    log.warn("커밋된 알림의 SSE 전송에 실패했습니다. type={}, count={}",
                            type, notifications.size(), exception);
                }
            }
        });
    }
}
