package com.memme.service.noti;

import com.memme.entity.auth.User;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationPreference;
import com.memme.entity.noti.NotificationType;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.noti.NotificationRepository;
import com.memme.repository.store.StoreRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SolutionReadyNotificationService {

    private static final String NOTIFICATION_TITLE = "새 솔루션이 준비되었습니다.";
    private static final String NOTIFICATION_CONTENT = "오늘의 솔루션을 확인해 주세요.";
    private static final String RELATED_ENTITY_TYPE = "SOLUTION_BUNDLE";

    private final StoreRepository storeRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public SolutionReadyNotificationService(
            StoreRepository storeRepository,
            NotificationPreferenceRepository preferenceRepository,
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.preferenceRepository = preferenceRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public void notifySolutionReady(SolutionBundleEntity bundle) {
        storeRepository.findOwnerUserIdByStoreId(bundle.getStoreId())
                .filter(this::isSolutionNotificationEnabled)
                .ifPresent(ownerUserId -> saveIfAbsent(ownerUserId, bundle.getId()));
    }

    private boolean isSolutionNotificationEnabled(Long ownerUserId) {
        return preferenceRepository.findByUserId(ownerUserId)
                .map(NotificationPreference::isSolutionEnabled)
                .orElse(false);
    }

    private void saveIfAbsent(Long ownerUserId, Long bundleId) {
        String notificationKey = "solution-ready:%d".formatted(bundleId);
        if (notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(
                ownerUserId,
                NotificationType.SOLUTION_READY,
                notificationKey
        )) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        User user = userRepository.getReferenceById(ownerUserId);
        notificationRepository.save(Notification.create(
                user,
                NotificationType.SOLUTION_READY,
                notificationKey,
                NOTIFICATION_TITLE,
                NOTIFICATION_CONTENT,
                RELATED_ENTITY_TYPE,
                bundleId,
                now,
                now
        ));
    }
}
