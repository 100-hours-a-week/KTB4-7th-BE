package com.memme.repository.noti;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.User;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationType;
import com.memme.entity.noti.NotificationPreference;
import com.memme.repository.auth.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class NotificationRepositoryIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 랭킹_알림_설정을_저장하고_기존_두_설정값을_보존한다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 11, 8, 0);
        User owner = userRepository.saveAndFlush(User.create(
                "ranking-pref@example.com", "encoded-password", "01011112222", now));
        NotificationPreference preference = NotificationPreference.create(owner, now);
        preference.updatePreferences(true, false, now);
        preferenceRepository.saveAndFlush(preference);
        entityManager.clear();

        NotificationPreference restored = preferenceRepository.findByUserId(owner.getId()).orElseThrow();
        assertThat(restored.isRankingChangeEnabled()).isTrue();
        assertThat(restored.isSolutionEnabled()).isTrue();
        assertThat(restored.isSalesUploadReminderEnabled()).isFalse();
        restored.updateRankingChangeEnabled(false, now.plusHours(1));
        preferenceRepository.flush();
        entityManager.clear();

        assertThat(preferenceRepository.findByUserId(owner.getId()).orElseThrow().isRankingChangeEnabled()).isFalse();
    }

    @Test
    void 같은_사용자의_랭킹_알림_멱등키는_중복_저장할_수_없다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 11, 8, 0);
        User owner = userRepository.saveAndFlush(User.create(
                "ranking-noti@example.com", "encoded-password", "01033334444", now));
        String key = "RANKING_CHANGED:" + owner.getId() + ":2026-09:2026-10-10";
        notificationRepository.saveAndFlush(Notification.create(owner, NotificationType.RANKING_CHANGED,
                key, "성장 랭킹 순위가 올랐어요", "5위예요.", "RANKING_SNAPSHOT", 87L, now, now));

        assertThat(notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(
                owner.getId(), NotificationType.RANKING_CHANGED, key)).isTrue();
        assertThatThrownBy(() -> notificationRepository.saveAndFlush(Notification.create(
                owner, NotificationType.RANKING_CHANGED, key, "중복", "중복", "RANKING_SNAPSHOT", 87L, now, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 사용자별_알림을_발송일시_최신순으로_조회한다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 25, 9, 0);
        User owner = userRepository.saveAndFlush(
                User.create("notification-owner@example.com", "encoded-password", "01012345678", now)
        );
        User otherUser = userRepository.saveAndFlush(
                User.create("notification-other@example.com", "encoded-password", "01087654321", now)
        );

        notificationRepository.saveAndFlush(Notification.create(
                owner,
                NotificationType.SOLUTION_READY,
                "solution:2026-09-25",
                "이전 알림",
                "이전 솔루션을 확인해 보세요.",
                "SOLUTION",
                10L,
                now.minusHours(1),
                now.minusHours(1)
        ));
        notificationRepository.saveAndFlush(Notification.create(
                owner,
                NotificationType.SALES_UPLOAD_REMINDER,
                "sales-upload:41:REMINDER:2026-09-25",
                "최근 알림",
                "매출 자료를 업로드해 주세요.",
                null,
                null,
                now,
                now
        ));
        notificationRepository.saveAndFlush(Notification.create(
                otherUser,
                NotificationType.SOLUTION_READY,
                "solution:2026-09-25",
                "다른 사용자의 알림",
                "다른 사용자에게만 보입니다.",
                "SOLUTION",
                20L,
                now.plusHours(1),
                now.plusHours(1)
        ));
        entityManager.clear();

        assertThat(notificationRepository.findAllByUserIdOrderBySentAtDesc(owner.getId()))
                .extracting(Notification::getTitle)
                .containsExactly("최근 알림", "이전 알림");
    }

    @Test
    void 사용자와_유형과_멱등키로_동일_알림의_생성_여부를_확인한다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 25, 9, 0);
        User owner = userRepository.saveAndFlush(
                User.create("notification-key-owner@example.com", "encoded-password", "01012341234", now)
        );
        notificationRepository.saveAndFlush(Notification.create(
                owner,
                NotificationType.SALES_UPLOAD_REMINDER,
                "sales-upload:42:REMINDER:2026-09-25",
                "매출 업로드 알림",
                "8월 매출 자료를 업로드해 주세요.",
                null,
                null,
                now,
                now
        ));

        assertThat(notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(
                owner.getId(), NotificationType.SALES_UPLOAD_REMINDER, "sales-upload:42:REMINDER:2026-09-25"
        )).isTrue();
        assertThat(notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(
                owner.getId(), NotificationType.SALES_UPLOAD_REMINDER, "sales-upload:42:REMINDER:2026-09-26"
        )).isFalse();
    }
}
