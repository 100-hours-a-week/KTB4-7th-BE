package com.memme.service.noti.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.entity.noti.NotificationType;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.noti.NotificationRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.store.StoreRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.data.domain.Pageable;

class SolutionReadyNotificationItemReaderTest {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 10, 24);

    @Test
    void 설정이_켜진_소유자의_미발송_번들만_읽고_커밋_위치를_재시작에_사용한다() throws Exception {
        SolutionBundleRepository bundles = mock(SolutionBundleRepository.class);
        StoreRepository stores = mock(StoreRepository.class);
        NotificationPreferenceRepository preferences = mock(NotificationPreferenceRepository.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        SolutionReadyNotificationItemReader reader = reader(bundles, stores, preferences, notifications);

        List<SolutionBundleEntity> firstPage = List.of(
                bundle(1L, 11L), bundle(2L, 12L), bundle(3L, 13L), bundle(4L, 14L), bundle(5L, 15L));
        List<StoreRepository.StoreOwner> firstPageOwners = List.of(
                owner(11L, 101L), owner(12L, 102L), owner(13L, 103L), owner(14L, 104L));
        List<StoreRepository.StoreOwner> restartedPageOwners = List.of(
                owner(12L, 102L), owner(13L, 103L), owner(14L, 104L));
        List<NotificationPreferenceRepository.SolutionPreferenceSetting> firstPagePreferences = List.of(
                preference(101L, true), preference(102L, false),
                preference(103L, true), preference(104L, true));
        List<NotificationPreferenceRepository.SolutionPreferenceSetting> restartedPagePreferences = List.of(
                preference(102L, false), preference(103L, true), preference(104L, true));
        NotificationRepository.NotificationUserKey alreadyNotified = existing(103L, "solution-ready:3");
        when(bundles.findByTargetDateAndStatusAndIdGreaterThanOrderByIdAsc(
                eq(TARGET_DATE), eq(SolutionBundleStatus.COMPLETED), eq(0L), any(Pageable.class)))
                .thenReturn(firstPage);
        when(bundles.findByTargetDateAndStatusAndIdGreaterThanOrderByIdAsc(
                eq(TARGET_DATE), eq(SolutionBundleStatus.COMPLETED), eq(5L), any(Pageable.class)))
                .thenReturn(List.of());
        when(bundles.findByTargetDateAndStatusAndIdGreaterThanOrderByIdAsc(
                eq(TARGET_DATE), eq(SolutionBundleStatus.COMPLETED), eq(1L), any(Pageable.class)))
                .thenReturn(firstPage.subList(1, firstPage.size()));
        when(stores.findStoreOwnersByStoreIds(Set.of(11L, 12L, 13L, 14L, 15L)))
                .thenReturn(firstPageOwners);
        when(stores.findStoreOwnersByStoreIds(Set.of(12L, 13L, 14L, 15L)))
                .thenReturn(restartedPageOwners);
        when(preferences.findSolutionPreferenceSettingsByUserIds(Set.of(101L, 102L, 103L, 104L)))
                .thenReturn(firstPagePreferences);
        when(preferences.findSolutionPreferenceSettingsByUserIds(Set.of(102L, 103L, 104L)))
                .thenReturn(restartedPagePreferences);
        when(notifications.findNotificationUserKeysByUserIdsAndTypeAndKeysIn(
                anyCollection(), eq(NotificationType.SOLUTION_READY), anyCollection()))
                .thenReturn(List.of(alreadyNotified));

        ExecutionContext checkpoint = new ExecutionContext();
        reader.open(checkpoint);

        SolutionReadyNotificationCandidate first = reader.read();
        assertThat(first).isEqualTo(new SolutionReadyNotificationCandidate(101L, 1L, "solution-ready:1"));
        reader.update(checkpoint);
        assertThat(checkpoint.getLong("solutionReadyNotificationReader.lastBundleId")).isEqualTo(1L);
        reader.close();

        SolutionReadyNotificationItemReader restarted = reader(bundles, stores, preferences, notifications);
        restarted.open(checkpoint);
        assertThat(restarted.read())
                .isEqualTo(new SolutionReadyNotificationCandidate(104L, 4L, "solution-ready:4"));
        assertThat(restarted.read()).isNull();
        restarted.close();

        verify(bundles).findByTargetDateAndStatusAndIdGreaterThanOrderByIdAsc(
                eq(TARGET_DATE), eq(SolutionBundleStatus.COMPLETED), eq(0L), any(Pageable.class));
        verify(bundles).findByTargetDateAndStatusAndIdGreaterThanOrderByIdAsc(
                eq(TARGET_DATE), eq(SolutionBundleStatus.COMPLETED), eq(1L), any(Pageable.class));
    }

    private SolutionReadyNotificationItemReader reader(
            SolutionBundleRepository bundles,
            StoreRepository stores,
            NotificationPreferenceRepository preferences,
            NotificationRepository notifications
    ) {
        return new SolutionReadyNotificationItemReader(
                bundles, stores, preferences, notifications, TARGET_DATE);
    }

    private SolutionBundleEntity bundle(Long id, Long storeId) {
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getId()).thenReturn(id);
        when(bundle.getStoreId()).thenReturn(storeId);
        return bundle;
    }

    private StoreRepository.StoreOwner owner(Long storeId, Long userId) {
        StoreRepository.StoreOwner owner = mock(StoreRepository.StoreOwner.class);
        when(owner.getStoreId()).thenReturn(storeId);
        when(owner.getOwnerUserId()).thenReturn(userId);
        return owner;
    }

    private NotificationPreferenceRepository.SolutionPreferenceSetting preference(Long userId, boolean enabled) {
        NotificationPreferenceRepository.SolutionPreferenceSetting preference =
                mock(NotificationPreferenceRepository.SolutionPreferenceSetting.class);
        when(preference.getUserId()).thenReturn(userId);
        when(preference.getSolutionEnabled()).thenReturn(enabled);
        return preference;
    }

    private NotificationRepository.NotificationUserKey existing(Long userId, String key) {
        NotificationRepository.NotificationUserKey existing = mock(NotificationRepository.NotificationUserKey.class);
        when(existing.getUserId()).thenReturn(userId);
        when(existing.getNotificationKey()).thenReturn(key);
        return existing;
    }
}
