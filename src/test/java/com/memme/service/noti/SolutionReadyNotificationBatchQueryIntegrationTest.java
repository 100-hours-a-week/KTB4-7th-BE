package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.User;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationPreference;
import com.memme.entity.noti.NotificationType;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.entity.store.Store;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.noti.NotificationRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.store.StoreRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class SolutionReadyNotificationBatchQueryIntegrationTest {

    @Autowired private SolutionReadyNotificationService notificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private SolutionBundleRepository solutionBundleRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private Clock clock;

    @Test
    void 완료_번들을_일괄_조회하고_설정과_중복을_유지하면서_SELECT를_네번만_실행한다() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        List<User> owners = new ArrayList<>();
        List<SolutionBundleEntity> bundles = new ArrayList<>();

        for (int index = 1; index <= 4; index++) {
            User owner = userRepository.save(User.create(
                    "solution-batch-owner-%d@example.com".formatted(index),
                    "encoded-password",
                    "010%08d".formatted(index),
                    now
            ));
            owners.add(owner);

            Store store = storeRepository.save(Store.create(
                    owner,
                    null,
                    null,
                    "솔루션 테스트 매장 %d".formatted(index),
                    "06236",
                    "서울특별시 강남구 테헤란로 123",
                    null,
                    now
            ));

            SolutionBundleEntity bundle = SolutionBundleEntity.create(store.getId(), (long) index, today);
            bundle.changeStatus(SolutionBundleStatus.COMPLETED);
            bundles.add(solutionBundleRepository.save(bundle));
        }

        SolutionBundleEntity bundleWithoutOwner = SolutionBundleEntity.create(99_999L, 5L, today);
        bundleWithoutOwner.changeStatus(SolutionBundleStatus.COMPLETED);
        bundles.add(solutionBundleRepository.save(bundleWithoutOwner));

        NotificationPreference enabled = NotificationPreference.create(owners.get(0), now);
        preferenceRepository.save(enabled);

        NotificationPreference disabled = NotificationPreference.create(owners.get(1), now);
        disabled.updatePreferences(false, null, now);
        preferenceRepository.save(disabled);

        NotificationPreference alreadyNotified = NotificationPreference.create(owners.get(3), now);
        preferenceRepository.save(alreadyNotified);

        String existingKey = "solution-ready:%d".formatted(bundles.get(3).getId());
        String notificationKeyForAnotherOwner = "solution-ready:%d".formatted(bundles.get(0).getId());
        notificationRepository.save(Notification.create(
                owners.get(3),
                NotificationType.SOLUTION_READY,
                existingKey,
                "새 솔루션이 준비되었습니다.",
                "오늘의 솔루션을 확인해 주세요.",
                "SOLUTION_BUNDLE",
                bundles.get(3).getId(),
                now,
                now
        ));
        notificationRepository.save(Notification.create(
                owners.get(3),
                NotificationType.SOLUTION_READY,
                notificationKeyForAnotherOwner,
                "이전 소유자에게 생성된 알림",
                "이전 소유자의 알림입니다.",
                "SOLUTION_BUNDLE",
                bundles.get(0).getId(),
                now,
                now
        ));
        entityManager.flush();
        List<Long> ownerIds = owners.stream().map(User::getId).toList();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        notificationService.notifyTodaysSolutions();
        entityManager.flush();

        assertThat(statistics.getQueryExecutionCount()).isEqualTo(4);
        assertThat(notificationRepository.findAllByUserIdOrderBySentAtDesc(ownerIds.get(0)))
                .extracting(Notification::getNotificationKey)
                .containsExactly("solution-ready:%d".formatted(bundles.get(0).getId()));
        assertThat(notificationRepository.findAllByUserIdOrderBySentAtDesc(ownerIds.get(1))).isEmpty();
        assertThat(notificationRepository.findAllByUserIdOrderBySentAtDesc(ownerIds.get(2))).isEmpty();
        assertThat(notificationRepository.findAllByUserIdOrderBySentAtDesc(ownerIds.get(3)))
                .extracting(Notification::getNotificationKey)
                .containsExactlyInAnyOrder(existingKey, notificationKeyForAnotherOwner);
    }
}
