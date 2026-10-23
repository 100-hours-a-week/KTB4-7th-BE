package com.memme.service.noti;

import com.memme.entity.auth.User;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationPreference;
import com.memme.entity.noti.NotificationType;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationBulkInsertRepository;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.noti.NotificationRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.store.StoreRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final NotificationBulkInsertRepository bulkInsertRepository;
    private final UserRepository userRepository;
    private final SolutionBundleRepository solutionBundleRepository;
    private final Clock clock;

    public SolutionReadyNotificationService(
            StoreRepository storeRepository,
            NotificationPreferenceRepository preferenceRepository,
            NotificationRepository notificationRepository,
            NotificationBulkInsertRepository bulkInsertRepository,
            UserRepository userRepository,
            SolutionBundleRepository solutionBundleRepository,
            Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.preferenceRepository = preferenceRepository;
        this.notificationRepository = notificationRepository;
        this.bulkInsertRepository = bulkInsertRepository;
        this.userRepository = userRepository;
        this.solutionBundleRepository = solutionBundleRepository;
        this.clock = clock;
    }

    /**
     * 매일 08시(스케줄러)에서 호출된다. 자정 배치 생성이든 업로드 직후 생성이든
     * 생성 시점과 무관하게, "오늘의 솔루션" 알림은 오늘 날짜로 COMPLETED된
     * 모든 번들에 대해 이 시점에 한 번만 발송한다.
     */
    @Transactional
    public void notifyTodaysSolutions() {
        LocalDate today = LocalDate.now(clock);
        List<SolutionBundleEntity> bundles = solutionBundleRepository.findAllByTargetDateAndStatus(
                today,
                SolutionBundleStatus.COMPLETED
        );
        if (bundles.isEmpty()) {
            return;
        }

        Set<Long> storeIds = bundles.stream()
                .map(SolutionBundleEntity::getStoreId)
                .collect(Collectors.toSet());
        Map<Long, Long> ownerUserIdByStoreId = storeRepository.findStoreOwnersByStoreIds(storeIds).stream()
                .collect(Collectors.toMap(
                        StoreRepository.StoreOwner::getStoreId,
                        StoreRepository.StoreOwner::getOwnerUserId
                ));
        if (ownerUserIdByStoreId.isEmpty()) {
            return;
        }

        Set<Long> ownerUserIds = Set.copyOf(ownerUserIdByStoreId.values());
        Map<Long, Boolean> solutionEnabledByUserId = preferenceRepository
                .findSolutionPreferenceSettingsByUserIds(ownerUserIds)
                .stream()
                .collect(Collectors.toMap(
                        NotificationPreferenceRepository.SolutionPreferenceSetting::getUserId,
                        NotificationPreferenceRepository.SolutionPreferenceSetting::getSolutionEnabled
                ));

        List<NotificationCandidate> candidates = new ArrayList<>();
        for (SolutionBundleEntity bundle : bundles) {
            Long ownerUserId = ownerUserIdByStoreId.get(bundle.getStoreId());
            if (ownerUserId == null || !Boolean.TRUE.equals(solutionEnabledByUserId.get(ownerUserId))) {
                continue;
            }
            candidates.add(new NotificationCandidate(
                    ownerUserId,
                    bundle.getId(),
                    "solution-ready:%d".formatted(bundle.getId())
            ));
        }
        if (candidates.isEmpty()) {
            return;
        }

        Set<Long> candidateOwnerUserIds = candidates.stream()
                .map(NotificationCandidate::ownerUserId)
                .collect(Collectors.toSet());
        Set<String> candidateNotificationKeys = candidates.stream()
                .map(NotificationCandidate::notificationKey)
                .collect(Collectors.toSet());
        Set<NotificationIdentity> existingNotifications = notificationRepository
                .findNotificationUserKeysByUserIdsAndTypeAndKeysIn(
                        candidateOwnerUserIds,
                        NotificationType.SOLUTION_READY,
                        candidateNotificationKeys
                )
                .stream()
                .map(existing -> new NotificationIdentity(existing.getUserId(), existing.getNotificationKey()))
                .collect(Collectors.toSet());

        List<NotificationBulkInsertRepository.NotificationRow> newNotifications = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);
        for (NotificationCandidate candidate : candidates) {
            NotificationIdentity identity = new NotificationIdentity(
                    candidate.ownerUserId(),
                    candidate.notificationKey()
            );
            if (!existingNotifications.contains(identity)) {
                newNotifications.add(new NotificationBulkInsertRepository.NotificationRow(
                        candidate.ownerUserId(),
                        NotificationType.SOLUTION_READY,
                        candidate.notificationKey(),
                        NOTIFICATION_TITLE,
                        NOTIFICATION_CONTENT,
                        RELATED_ENTITY_TYPE,
                        candidate.bundleId(),
                        now,
                        now
                ));
            }
        }
        if (!newNotifications.isEmpty()) {
            bulkInsertRepository.insertAll(newNotifications);
        }
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

        saveNotification(ownerUserId, bundleId, notificationKey);
    }

    private void saveNotification(Long ownerUserId, Long bundleId, String notificationKey) {
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

    private record NotificationCandidate(Long ownerUserId, Long bundleId, String notificationKey) {
    }

    private record NotificationIdentity(Long ownerUserId, String notificationKey) {
    }
}
