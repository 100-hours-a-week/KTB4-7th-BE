package com.memme.repository.noti;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.User;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationType;
import com.memme.entity.noti.NotificationPreference;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationSseQueryRepository.NotificationIdentity;
import com.memme.repository.noti.NotificationSseQueryRepository.StoredNotification;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    void 사용자와_유형과_멱등키에_맞는_저장_알림을_SSE용으로_묶음_조회한다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 8, 0);
        User owner = userRepository.saveAndFlush(User.create(
                "sse-notification-owner@example.com", "encoded-password", "01012121212", now));
        User otherUser = userRepository.saveAndFlush(User.create(
                "sse-notification-other@example.com", "encoded-password", "01034343434", now));

        Notification matching = Notification.create(owner, NotificationType.SOLUTION_READY,
                "solution-ready:10", "솔루션 도착", "솔루션을 확인해 주세요.", "SOLUTION_BUNDLE", 10L, now, now);
        matching.markAsRead(now.plusMinutes(5));
        notificationRepository.saveAndFlush(matching);
        notificationRepository.saveAndFlush(Notification.create(owner, NotificationType.SOLUTION_READY,
                "solution-ready:11", "요청하지 않은 사용자 키 조합", "다른 알림", "SOLUTION_BUNDLE", 11L, now, now));
        Notification otherUsersNotification = Notification.create(otherUser, NotificationType.SOLUTION_READY,
                "solution-ready:11", "다른 사용자 알림", "솔루션을 확인해 주세요.", "SOLUTION_BUNDLE", 11L, now, now);
        notificationRepository.saveAndFlush(otherUsersNotification);
        notificationRepository.saveAndFlush(Notification.create(owner, NotificationType.SALES_UPLOAD_REMINDER,
                "solution-ready:10", "다른 유형", "다른 알림", null, null, now, now));
        notificationRepository.saveAndFlush(Notification.create(otherUser, NotificationType.SOLUTION_READY,
                "solution-ready:10", "다른 사용자", "다른 알림", "SOLUTION_BUNDLE", 10L, now, now));
        for (NotificationType type : NotificationType.values()) {
            String key = "sse:" + type.name();
            notificationRepository.saveAndFlush(Notification.create(owner, type,
                    key, type.name(), "알림 내용", "SOLUTION_BUNDLE", 10L, now, now));
        }
        entityManager.clear();

        List<StoredNotification> results = notificationRepository
                .findSseNotificationsByIdentitiesAndType(
                        List.of(
                                new NotificationIdentity(owner.getId(), "solution-ready:10"),
                                new NotificationIdentity(otherUser.getId(), "solution-ready:11")
                        ),
                        NotificationType.SOLUTION_READY
                );

        assertThat(results).hasSize(2);
        StoredNotification stored = results.stream()
                .filter(notification -> notification.notificationKey().equals("solution-ready:10"))
                .findFirst()
                .orElseThrow();
        assertThat(stored.id()).isEqualTo(matching.getId());
        assertThat(stored.userId()).isEqualTo(owner.getId());
        assertThat(stored.notificationType()).isEqualTo(NotificationType.SOLUTION_READY);
        assertThat(stored.notificationKey()).isEqualTo("solution-ready:10");
        assertThat(stored.title()).isEqualTo("솔루션 도착");
        assertThat(stored.content()).isEqualTo("솔루션을 확인해 주세요.");
        assertThat(stored.relatedEntityType()).isEqualTo("SOLUTION_BUNDLE");
        assertThat(stored.relatedEntityId()).isEqualTo(10L);
        assertThat(stored.sentAt()).isEqualTo(now);
        assertThat(stored.readAt()).isEqualTo(now.plusMinutes(5));

        StoredNotification otherUsersStored = results.stream()
                .filter(notification -> notification.userId().equals(otherUser.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(otherUsersStored.id()).isEqualTo(otherUsersNotification.getId());

        assertThat(results).extracting(StoredNotification::userId, StoredNotification::notificationKey)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(owner.getId(), "solution-ready:10"),
                        org.assertj.core.groups.Tuple.tuple(otherUser.getId(), "solution-ready:11")
                );

        for (NotificationType type : NotificationType.values()) {
            List<StoredNotification> notifications = notificationRepository
                    .findSseNotificationsByIdentitiesAndType(
                            List.of(new NotificationIdentity(owner.getId(), "sse:" + type.name())), type
                    );

            assertThat(notifications).singleElement()
                    .extracting(StoredNotification::notificationType)
                    .isEqualTo(type);
        }

        assertThat(notificationRepository.findSseNotificationsByIdentitiesAndType(
                List.of(), NotificationType.SOLUTION_READY)).isEmpty();
    }
}
