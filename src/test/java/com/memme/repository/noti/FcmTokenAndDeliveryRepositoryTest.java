package com.memme.repository.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.entity.auth.User;
import com.memme.entity.noti.FcmDelivery;
import com.memme.entity.noti.FcmToken;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationType;
import com.memme.repository.auth.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FcmTokenAndDeliveryRepositoryTest {

    @Autowired private FcmTokenRepository fcmTokenRepository;
    @Autowired private FcmDeliveryRepository fcmDeliveryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void 사용자별_FCM_토큰만_조회한다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 28, 9, 0);
        User user = saveUser("fcm-owner@example.com", "01012345671", now);
        User otherUser = saveUser("fcm-other@example.com", "01012345672", now);
        FcmToken ownerToken = saveToken(user, "device-token-1", hash('a'), now);
        saveToken(otherUser, "device-token-2", hash('b'), now);
        entityManager.clear();

        assertThat(fcmTokenRepository.findAllByUserId(user.getId()))
                .extracting(FcmToken::getId)
                .containsExactly(ownerToken.getId());
    }

    @Test
    void 같은_FCM_토큰은_다른_사용자에게_중복_등록할_수_없다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 28, 9, 0);
        User user = saveUser("fcm-unique-owner@example.com", "01012345673", now);
        User otherUser = saveUser("fcm-unique-other@example.com", "01012345674", now);
        String tokenHash = hash('c');
        saveToken(user, "shared-device-token", tokenHash, now);

        assertThatThrownBy(() -> fcmTokenRepository.saveAndFlush(
                FcmToken.register(otherUser, "shared-device-token", tokenHash, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 토큰_갱신_시_토큰과_해시를_함께_갱신한다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 28, 9, 0);
        User user = saveUser("fcm-refresh-owner@example.com", "01012345675", now);
        FcmToken token = saveToken(user, "old-device-token", hash('d'), now);

        token.refresh("new-device-token", hash('e'), now.plusMinutes(1));
        fcmTokenRepository.flush();
        entityManager.clear();

        FcmToken refreshed = fcmTokenRepository.findByTokenHash(hash('e')).orElseThrow();
        assertThat(refreshed.getId()).isEqualTo(token.getId());
        assertThat(refreshed.getTokenValue()).isEqualTo("new-device-token");
        assertThat(refreshed.getTokenHash()).isEqualTo(hash('e'));
    }

    @Test
    void 발송_시도와_실패_후_성공_상태를_저장한다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 28, 9, 0);
        User user = saveUser("fcm-delivery-owner@example.com", "01012345676", now);
        FcmToken token = saveToken(user, "delivery-device-token", hash('f'), now);
        Notification notification = notificationRepository.saveAndFlush(Notification.create(
                user, NotificationType.SOLUTION_READY, "fcm-delivery-test", "솔루션 도착",
                "새 솔루션을 확인해 주세요.", "SOLUTION_BUNDLE", 1L, now, now));
        FcmDelivery delivery = fcmDeliveryRepository.saveAndFlush(
                FcmDelivery.pending(notification, token, token.getTokenHash(), now));

        delivery.markAttemptStarted(now.plusSeconds(1));
        delivery.markFailed("UNREGISTERED", now.plusSeconds(2));
        fcmDeliveryRepository.flush();
        entityManager.clear();

        FcmDelivery failed = fcmDeliveryRepository
                .findByNotificationIdAndTokenHash(notification.getId(), token.getTokenHash())
                .orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(FcmDelivery.Status.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getLastErrorCode()).isEqualTo("UNREGISTERED");

        failed.markAttemptStarted(now.plusSeconds(3));
        failed.markSent(now.plusSeconds(4));
        fcmDeliveryRepository.flush();
        entityManager.clear();

        FcmDelivery sent = fcmDeliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(FcmDelivery.Status.SENT);
        assertThat(sent.getAttemptCount()).isEqualTo(2);
        assertThat(sent.getLastErrorCode()).isNull();
        assertThat(sent.getSentAt()).isEqualTo(now.plusSeconds(4));
        assertThat(fcmDeliveryRepository.findAllByStatusOrderByCreatedAtAsc(FcmDelivery.Status.SENT))
                .extracting(FcmDelivery::getId)
                .containsExactly(delivery.getId());
    }

    private User saveUser(String email, String phone, LocalDateTime now) {
        return userRepository.saveAndFlush(User.create(email, "encoded-password", phone, now));
    }

    private FcmToken saveToken(User user, String tokenValue, String tokenHash, LocalDateTime now) {
        return fcmTokenRepository.saveAndFlush(FcmToken.register(user, tokenValue, tokenHash, now));
    }

    private String hash(char value) {
        return String.valueOf(value).repeat(64);
    }
}
