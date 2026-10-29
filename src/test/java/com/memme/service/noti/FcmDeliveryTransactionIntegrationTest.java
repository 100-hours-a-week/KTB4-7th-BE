package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.entity.auth.User;
import com.memme.entity.noti.FcmToken;
import com.memme.entity.noti.FcmDelivery;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationType;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.FcmDeliveryRepository;
import com.memme.repository.noti.FcmTokenRepository;
import com.memme.repository.noti.NotificationBulkInsertRepository;
import com.memme.repository.noti.NotificationRepository;
import com.memme.service.noti.fcm.FcmClient;
import com.memme.service.noti.fcm.FcmSendResult;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class FcmDeliveryTransactionIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationBulkInsertRepository bulkInsertRepository;
    @Autowired private FcmTokenRepository tokenRepository;
    @Autowired private FcmDeliveryRepository deliveryRepository;
    @Autowired private FcmDeliveryEnqueueService enqueueService;
    @Autowired private FcmDeliveryAttemptService attemptService;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private FcmClient fcmClient;

    @Test
    void JPA_알림_저장_트랜잭션이_롤백되면_발송_대기도_남지_않는다() {
        User user = user("fcm-jpa-rollback@example.com", "01099110001");
        token(user, 'a');
        long deliveryCountBefore = deliveryRepository.count();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
            Notification notification = notificationRepository.save(notification(user, "jpa-rollback"));
            enqueueService.enqueueSaved(List.of(notification));
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(
                user.getId(), NotificationType.SOLUTION_READY, "jpa-rollback")).isFalse();
        assertThat(deliveryRepository.count()).isEqualTo(deliveryCountBefore);
    }

    @Test
    void 벌크_알림과_발송_대기는_같은_트랜잭션에서_커밋된다() {
        User user = user("fcm-bulk-commit@example.com", "01099110002");
        token(user, 'b');
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        NotificationBulkInsertRepository.NotificationRow row = row(user.getId(), "bulk-commit");

        transaction.executeWithoutResult(ignored -> {
            bulkInsertRepository.insertAll(List.of(row));
            enqueueService.enqueueBulkRows(List.of(row));
        });

        Notification notification = notificationRepository.findAllByUserIdOrderBySentAtDesc(user.getId())
                .stream().filter(value -> "bulk-commit".equals(value.getNotificationKey())).findFirst().orElseThrow();
        assertThat(deliveryRepository.findAllByNotificationIdOrderByIdAsc(notification.getId()))
                .singleElement().satisfies(delivery -> {
                    assertThat(delivery.getStatus()).isEqualTo(com.memme.entity.noti.FcmDelivery.Status.PENDING);
                    assertThat(delivery.getAttemptCount()).isZero();
                });
        assertThat(deliveryRepository.findReadyToAttempt(
                FcmDelivery.Status.PENDING,
                List.of(FcmDelivery.Status.PENDING, FcmDelivery.Status.FAILED),
                FcmDeliveryAttemptService.RETRYABLE_ERRORS.stream().toList(),
                now().minusMinutes(1), now().minusMinutes(5), PageRequest.of(0, 100)))
                .extracting(FcmDelivery::getNotification)
                .extracting(Notification::getId)
                .contains(notification.getId());
    }

    @Test
    void 벌크_알림_트랜잭션이_롤백되면_발송_대기도_남지_않는다() {
        User user = user("fcm-bulk-rollback@example.com", "01099110003");
        token(user, 'c');
        long deliveryCountBefore = deliveryRepository.count();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        NotificationBulkInsertRepository.NotificationRow row = row(user.getId(), "bulk-rollback");

        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
            bulkInsertRepository.insertAll(List.of(row));
            enqueueService.enqueueBulkRows(List.of(row));
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(
                user.getId(), NotificationType.SOLUTION_READY, "bulk-rollback")).isFalse();
        assertThat(deliveryRepository.count()).isEqualTo(deliveryCountBefore);
    }

    @Test
    void 무효_토큰을_삭제해도_발송_실패_기록은_남는다() {
        User user = user("fcm-invalid-token@example.com", "01099110004");
        token(user, 'd');
        Notification notification = notificationRepository.save(notification(user, "invalid-token"));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(ignored -> enqueueService.enqueueSaved(List.of(notification)));
        FcmDelivery delivery = deliveryRepository.findAllByNotificationIdOrderByIdAsc(notification.getId()).getFirst();
        when(fcmClient.send(any())).thenReturn(FcmSendResult.invalidToken("UNREGISTERED"));

        attemptService.attempt(delivery.getId());

        assertThat(tokenRepository.findAllByUserId(user.getId())).isEmpty();
        FcmDelivery failed = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(FcmDelivery.Status.FAILED);
        assertThat(failed.getFcmToken()).isNull();
        assertThat(failed.getLastErrorCode()).isEqualTo("UNREGISTERED");
    }

    private User user(String email, String phone) {
        return userRepository.save(User.create(email, "encoded-password", phone, now()));
    }

    private void token(User user, char hashCharacter) {
        tokenRepository.save(FcmToken.register(
                user, "fcm-test-token-" + hashCharacter,
                String.valueOf(hashCharacter).repeat(64), now()));
    }

    private Notification notification(User user, String key) {
        return Notification.create(user, NotificationType.SOLUTION_READY, key,
                "새 솔루션", "확인해 주세요", "SOLUTION_BUNDLE", 1L, now(), now());
    }

    private NotificationBulkInsertRepository.NotificationRow row(Long userId, String key) {
        return new NotificationBulkInsertRepository.NotificationRow(userId, NotificationType.SOLUTION_READY,
                key, "새 솔루션", "확인해 주세요", "SOLUTION_BUNDLE", 1L, now(), now());
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 10, 29, 8, 0);
    }
}
