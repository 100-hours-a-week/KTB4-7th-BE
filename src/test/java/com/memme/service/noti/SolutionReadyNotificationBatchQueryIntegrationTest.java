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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:solution-ready-query-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class SolutionReadyNotificationBatchQueryIntegrationTest {

    @Autowired private JobOperator jobOperator;
    @Autowired @Qualifier("solutionReadyNotificationJob") private Job notificationJob;
    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private SolutionBundleRepository solutionBundleRepository;
    @Autowired private Clock clock;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void initializeBatchMetadata() throws Exception {
        com.memme.service.noti.batch.BatchMetadataTestSupport.initializeSchemaIfNeeded(dataSource);
        com.memme.service.noti.batch.BatchMetadataTestSupport.clearExecutions(jdbcTemplate);
    }

    @Test
    void 오늘_완료된_번들에서_알림_설정과_소유자별_중복_조건을_유지한다() throws Exception {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        List<User> owners = new java.util.ArrayList<>();
        List<SolutionBundleEntity> bundles = new java.util.ArrayList<>();

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

        preferenceRepository.save(NotificationPreference.create(owners.get(0), now));
        NotificationPreference disabled = NotificationPreference.create(owners.get(1), now);
        disabled.updatePreferences(false, null, now);
        preferenceRepository.save(disabled);
        preferenceRepository.save(NotificationPreference.create(owners.get(3), now));

        String alreadyNotifiedKey = "solution-ready:%d".formatted(bundles.get(3).getId());
        String sameKeyForDifferentOwner = "solution-ready:%d".formatted(bundles.get(0).getId());
        notificationRepository.save(notification(owners.get(3), alreadyNotifiedKey, bundles.get(3).getId(), now));
        notificationRepository.save(notification(owners.get(3), sameKeyForDifferentOwner, bundles.get(0).getId(), now));

        JobExecution execution = jobOperator.start(
                notificationJob,
                new JobParametersBuilder().addLocalDate("targetDate", today).toJobParameters()
        );

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(notificationRepository.findAllByUserIdOrderBySentAtDesc(owners.get(0).getId()))
                .extracting(Notification::getNotificationKey)
                .containsExactly("solution-ready:%d".formatted(bundles.get(0).getId()));
        assertThat(notificationRepository.findAllByUserIdOrderBySentAtDesc(owners.get(1).getId())).isEmpty();
        assertThat(notificationRepository.findAllByUserIdOrderBySentAtDesc(owners.get(2).getId())).isEmpty();
        assertThat(notificationRepository.findAllByUserIdOrderBySentAtDesc(owners.get(3).getId()))
                .extracting(Notification::getNotificationKey)
                .containsExactlyInAnyOrder(alreadyNotifiedKey, sameKeyForDifferentOwner);
    }

    private Notification notification(User user, String key, Long bundleId, LocalDateTime now) {
        return Notification.create(
                user,
                NotificationType.SOLUTION_READY,
                key,
                "새 솔루션이 준비되었습니다.",
                "오늘의 솔루션을 확인해 주세요.",
                "SOLUTION_BUNDLE",
                bundleId,
                now,
                now
        );
    }
}
