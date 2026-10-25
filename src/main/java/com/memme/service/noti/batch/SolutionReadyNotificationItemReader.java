package com.memme.service.noti.batch;

import com.memme.entity.noti.NotificationType;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.noti.NotificationRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.store.StoreRepository;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.data.domain.PageRequest;

public class SolutionReadyNotificationItemReader implements ItemStreamReader<SolutionReadyNotificationCandidate> {

    private static final int READ_PAGE_SIZE = 100;
    private static final long INITIAL_BUNDLE_ID = 0L;
    private static final String CHECKPOINT_KEY = "solutionReadyNotificationReader.lastBundleId";

    private final SolutionBundleRepository solutionBundleRepository;
    private final StoreRepository storeRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationRepository notificationRepository;
    private final LocalDate targetDate;

    private final ArrayDeque<SolutionReadyNotificationCandidate> candidates = new ArrayDeque<>();
    private long scanAfterBundleId = INITIAL_BUNDLE_ID;
    private long lastReturnedBundleId = INITIAL_BUNDLE_ID;
    private boolean exhausted;

    public SolutionReadyNotificationItemReader(
            SolutionBundleRepository solutionBundleRepository,
            StoreRepository storeRepository,
            NotificationPreferenceRepository preferenceRepository,
            NotificationRepository notificationRepository,
            LocalDate targetDate
    ) {
        this.solutionBundleRepository = solutionBundleRepository;
        this.storeRepository = storeRepository;
        this.preferenceRepository = preferenceRepository;
        this.notificationRepository = notificationRepository;
        this.targetDate = targetDate;
    }

    @Override
    public SolutionReadyNotificationCandidate read() {
        while (candidates.isEmpty() && !exhausted) {
            loadNextPage();
        }
        if (candidates.isEmpty()) {
            return null;
        }

        SolutionReadyNotificationCandidate candidate = candidates.removeFirst();
        lastReturnedBundleId = candidate.bundleId();
        return candidate;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        long checkpoint = executionContext.getLong(CHECKPOINT_KEY, INITIAL_BUNDLE_ID);
        scanAfterBundleId = checkpoint;
        lastReturnedBundleId = checkpoint;
        candidates.clear();
        exhausted = false;
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putLong(CHECKPOINT_KEY, lastReturnedBundleId);
    }

    @Override
    public void close() throws ItemStreamException {
        candidates.clear();
        exhausted = false;
    }

    private void loadNextPage() {
        List<SolutionBundleEntity> bundles = solutionBundleRepository
                .findByTargetDateAndStatusAndIdGreaterThanOrderByIdAsc(
                        targetDate,
                        SolutionBundleStatus.COMPLETED,
                        scanAfterBundleId,
                        PageRequest.of(0, READ_PAGE_SIZE)
                );
        if (bundles.isEmpty()) {
            exhausted = true;
            return;
        }
        scanAfterBundleId = bundles.getLast().getId();

        Set<Long> storeIds = bundles.stream()
                .map(SolutionBundleEntity::getStoreId)
                .collect(Collectors.toSet());
        Map<Long, Long> ownerUserIdByStoreId = new HashMap<>();
        for (StoreRepository.StoreOwner owner : storeRepository.findStoreOwnersByStoreIds(storeIds)) {
            ownerUserIdByStoreId.put(owner.getStoreId(), owner.getOwnerUserId());
        }
        if (ownerUserIdByStoreId.isEmpty()) {
            return;
        }

        Set<Long> ownerUserIds = Set.copyOf(ownerUserIdByStoreId.values());
        Map<Long, Boolean> solutionEnabledByUserId = new HashMap<>();
        for (NotificationPreferenceRepository.SolutionPreferenceSetting preference
                : preferenceRepository.findSolutionPreferenceSettingsByUserIds(ownerUserIds)) {
            solutionEnabledByUserId.put(preference.getUserId(), preference.getSolutionEnabled());
        }

        List<SolutionReadyNotificationCandidate> pageCandidates = new ArrayList<>();
        for (SolutionBundleEntity bundle : bundles) {
            Long ownerUserId = ownerUserIdByStoreId.get(bundle.getStoreId());
            if (ownerUserId == null || !Boolean.TRUE.equals(solutionEnabledByUserId.get(ownerUserId))) {
                continue;
            }
            pageCandidates.add(new SolutionReadyNotificationCandidate(
                    ownerUserId,
                    bundle.getId(),
                    "solution-ready:%d".formatted(bundle.getId())
            ));
        }
        if (pageCandidates.isEmpty()) {
            return;
        }

        Set<Long> candidateOwnerUserIds = new HashSet<>();
        Set<String> candidateNotificationKeys = new HashSet<>();
        for (SolutionReadyNotificationCandidate candidate : pageCandidates) {
            candidateOwnerUserIds.add(candidate.ownerUserId());
            candidateNotificationKeys.add(candidate.notificationKey());
        }
        Set<NotificationIdentity> existingNotifications = new HashSet<>();
        for (NotificationRepository.NotificationUserKey existing : notificationRepository
                .findNotificationUserKeysByUserIdsAndTypeAndKeysIn(
                        candidateOwnerUserIds,
                        NotificationType.SOLUTION_READY,
                        candidateNotificationKeys
                )) {
            existingNotifications.add(new NotificationIdentity(existing.getUserId(), existing.getNotificationKey()));
        }

        for (SolutionReadyNotificationCandidate candidate : pageCandidates) {
            if (!existingNotifications.contains(
                    new NotificationIdentity(candidate.ownerUserId(), candidate.notificationKey()))) {
                candidates.addLast(candidate);
            }
        }
    }

    private record NotificationIdentity(Long ownerUserId, String notificationKey) {
    }
}
