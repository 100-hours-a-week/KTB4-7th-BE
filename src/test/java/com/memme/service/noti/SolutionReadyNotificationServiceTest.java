package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.entity.auth.User;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationPreference;
import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationBulkInsertRepository;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.noti.NotificationRepository;
import com.memme.repository.solution.SolutionBundleRepository;
import com.memme.repository.store.StoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SolutionReadyNotificationServiceTest {

    private StoreRepository storeRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private NotificationRepository notificationRepository;
    private NotificationBulkInsertRepository bulkInsertRepository;
    private UserRepository userRepository;
    private SolutionBundleRepository solutionBundleRepository;
    private Clock clock;
    private SolutionReadyNotificationService service;

    @BeforeEach
    void setUp() {
        storeRepository = mock(StoreRepository.class);
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        bulkInsertRepository = mock(NotificationBulkInsertRepository.class);
        userRepository = mock(UserRepository.class);
        solutionBundleRepository = mock(SolutionBundleRepository.class);
        clock = Clock.fixed(Instant.parse("2026-09-26T00:00:00Z"), ZoneOffset.UTC);
        service = new SolutionReadyNotificationService(
                storeRepository,
                preferenceRepository,
                notificationRepository,
                bulkInsertRepository,
                userRepository,
                solutionBundleRepository,
                clock
        );
    }

    @Test
    void 솔루션_알림_설정_사용자에게_완료_알림을_생성한다() {
        SolutionBundleEntity bundle = solutionBundle(20L, 10L);
        givenEnabledOwner(10L, 3L);
        User user = mock(User.class);
        when(userRepository.getReferenceById(3L)).thenReturn(user);
        when(notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(any(), any(), any()))
                .thenReturn(false);

        service.notifySolutionReady(bundle);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.getNotificationKey()).isEqualTo("solution-ready:20");
        assertThat(notification.getTitle()).isEqualTo("새 솔루션이 준비되었습니다.");
        assertThat(notification.getContent()).isEqualTo("오늘의 솔루션을 확인해 주세요.");
        assertThat(notification.getRelatedEntityType()).isEqualTo("SOLUTION_BUNDLE");
        assertThat(notification.getRelatedEntityId()).isEqualTo(20L);
    }

    @Test
    void 솔루션_알림_설정이_꺼져_있으면_알림을_생성하지_않는다() {
        SolutionBundleEntity bundle = solutionBundle(20L, 10L);
        when(storeRepository.findOwnerUserIdByStoreId(10L)).thenReturn(Optional.of(3L));
        NotificationPreference preference = mock(NotificationPreference.class);
        when(preference.isSolutionEnabled()).thenReturn(false);
        when(preferenceRepository.findByUserId(3L)).thenReturn(Optional.of(preference));

        service.notifySolutionReady(bundle);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void 같은_솔루션_묶음의_알림은_중복_생성하지_않는다() {
        SolutionBundleEntity bundle = solutionBundle(20L, 10L);
        givenEnabledOwner(10L, 3L);
        when(notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(any(), any(), any()))
                .thenReturn(true);

        service.notifySolutionReady(bundle);

        verify(notificationRepository, never()).save(any());
        verify(userRepository, never()).getReferenceById(any());
    }

    @Test
    void 오늘_완료된_모든_번들에_대해_알림을_발송한다() {
        LocalDate today = LocalDate.now(clock);
        SolutionBundleEntity first = solutionBundle(20L, 10L);
        SolutionBundleEntity second = solutionBundle(21L, 11L);
        when(solutionBundleRepository.findAllByTargetDateAndStatus(today, SolutionBundleStatus.COMPLETED))
                .thenReturn(List.of(first, second));

        StoreRepository.StoreOwner firstOwner = storeOwner(10L, 3L);
        StoreRepository.StoreOwner secondOwner = storeOwner(11L, 4L);
        when(storeRepository.findStoreOwnersByStoreIds(Set.of(10L, 11L)))
                .thenReturn(List.of(firstOwner, secondOwner));
        NotificationPreferenceRepository.SolutionPreferenceSetting firstPreference =
                solutionPreference(3L, true);
        NotificationPreferenceRepository.SolutionPreferenceSetting secondPreference =
                solutionPreference(4L, true);
        when(preferenceRepository.findSolutionPreferenceSettingsByUserIds(Set.of(3L, 4L)))
                .thenReturn(List.of(firstPreference, secondPreference));
        when(notificationRepository.findNotificationUserKeysByUserIdsAndTypeAndKeysIn(
                Set.of(3L, 4L),
                com.memme.entity.noti.NotificationType.SOLUTION_READY,
                Set.of("solution-ready:20", "solution-ready:21")
        )).thenReturn(List.of());
        when(userRepository.getReferenceById(any())).thenReturn(mock(User.class));

        service.notifyTodaysSolutions();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationBulkInsertRepository.NotificationRow>> rows =
                ArgumentCaptor.forClass(List.class);
        verify(bulkInsertRepository).insertAll(rows.capture());
        assertThat(rows.getValue()).hasSize(2)
                .extracting(NotificationBulkInsertRepository.NotificationRow::notificationKey)
                .containsExactlyInAnyOrder("solution-ready:20", "solution-ready:21");
        verify(notificationRepository, never()).save(any());
        verify(storeRepository).findStoreOwnersByStoreIds(Set.of(10L, 11L));
        verify(preferenceRepository).findSolutionPreferenceSettingsByUserIds(Set.of(3L, 4L));
        verify(notificationRepository).findNotificationUserKeysByUserIdsAndTypeAndKeysIn(
                Set.of(3L, 4L),
                com.memme.entity.noti.NotificationType.SOLUTION_READY,
                Set.of("solution-ready:20", "solution-ready:21")
        );
        verify(storeRepository, never()).findOwnerUserIdByStoreId(anyLong());
        verify(preferenceRepository, never()).findByUserId(anyLong());
        verify(notificationRepository, never()).existsByUserIdAndNotificationTypeAndNotificationKey(
                anyLong(), any(), any());
    }

    @Test
    void 알림_설정이_꺼진_번들은_벌크_저장_대상에서_제외한다() {
        LocalDate today = LocalDate.now(clock);
        SolutionBundleEntity first = solutionBundle(20L, 10L);
        SolutionBundleEntity second = solutionBundle(21L, 11L);
        StoreRepository.StoreOwner firstOwner = storeOwner(10L, 3L);
        StoreRepository.StoreOwner secondOwner = storeOwner(11L, 4L);
        NotificationPreferenceRepository.SolutionPreferenceSetting firstPreference =
                solutionPreference(3L, true);
        NotificationPreferenceRepository.SolutionPreferenceSetting secondPreference =
                solutionPreference(4L, false);
        when(solutionBundleRepository.findAllByTargetDateAndStatus(today, SolutionBundleStatus.COMPLETED))
                .thenReturn(List.of(first, second));
        when(storeRepository.findStoreOwnersByStoreIds(Set.of(10L, 11L)))
                .thenReturn(List.of(firstOwner, secondOwner));
        when(preferenceRepository.findSolutionPreferenceSettingsByUserIds(Set.of(3L, 4L)))
                .thenReturn(List.of(firstPreference, secondPreference));
        when(notificationRepository.findNotificationUserKeysByUserIdsAndTypeAndKeysIn(
                Set.of(3L), com.memme.entity.noti.NotificationType.SOLUTION_READY,
                Set.of("solution-ready:20"))).thenReturn(List.of());

        service.notifyTodaysSolutions();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationBulkInsertRepository.NotificationRow>> rows =
                ArgumentCaptor.forClass(List.class);
        verify(bulkInsertRepository).insertAll(rows.capture());
        assertThat(rows.getValue()).hasSize(1)
                .extracting(NotificationBulkInsertRepository.NotificationRow::notificationKey)
                .containsExactly("solution-ready:20");
    }

    @Test
    void 이미_알림이_있으면_벌크_저장을_다시_호출하지_않는다() {
        LocalDate today = LocalDate.now(clock);
        SolutionBundleEntity bundle = solutionBundle(20L, 10L);
        StoreRepository.StoreOwner owner = storeOwner(10L, 3L);
        NotificationPreferenceRepository.SolutionPreferenceSetting preference =
                solutionPreference(3L, true);
        when(solutionBundleRepository.findAllByTargetDateAndStatus(today, SolutionBundleStatus.COMPLETED))
                .thenReturn(List.of(bundle));
        when(storeRepository.findStoreOwnersByStoreIds(Set.of(10L)))
                .thenReturn(List.of(owner));
        when(preferenceRepository.findSolutionPreferenceSettingsByUserIds(Set.of(3L)))
                .thenReturn(List.of(preference));
        NotificationRepository.NotificationUserKey existing = mock(NotificationRepository.NotificationUserKey.class);
        when(existing.getUserId()).thenReturn(3L);
        when(existing.getNotificationKey()).thenReturn("solution-ready:20");
        when(notificationRepository.findNotificationUserKeysByUserIdsAndTypeAndKeysIn(
                Set.of(3L), com.memme.entity.noti.NotificationType.SOLUTION_READY,
                Set.of("solution-ready:20"))).thenReturn(List.of(existing));

        service.notifyTodaysSolutions();

        verify(bulkInsertRepository, never()).insertAll(any());
    }

    @Test
    void 오늘_완료된_번들이_없으면_알림을_발송하지_않는다() {
        LocalDate today = LocalDate.now(clock);
        when(solutionBundleRepository.findAllByTargetDateAndStatus(today, SolutionBundleStatus.COMPLETED))
                .thenReturn(List.of());

        service.notifyTodaysSolutions();

        verify(notificationRepository, never()).save(any());
    }

    private void givenEnabledOwner(Long storeId, Long ownerUserId) {
        when(storeRepository.findOwnerUserIdByStoreId(storeId)).thenReturn(Optional.of(ownerUserId));
        NotificationPreference preference = mock(NotificationPreference.class);
        when(preference.isSolutionEnabled()).thenReturn(true);
        when(preferenceRepository.findByUserId(ownerUserId)).thenReturn(Optional.of(preference));
    }

    private StoreRepository.StoreOwner storeOwner(Long storeId, Long ownerUserId) {
        StoreRepository.StoreOwner storeOwner = mock(StoreRepository.StoreOwner.class);
        when(storeOwner.getStoreId()).thenReturn(storeId);
        when(storeOwner.getOwnerUserId()).thenReturn(ownerUserId);
        return storeOwner;
    }

    private NotificationPreferenceRepository.SolutionPreferenceSetting solutionPreference(
            Long userId,
            boolean enabled
    ) {
        NotificationPreferenceRepository.SolutionPreferenceSetting preference =
                mock(NotificationPreferenceRepository.SolutionPreferenceSetting.class);
        when(preference.getUserId()).thenReturn(userId);
        when(preference.getSolutionEnabled()).thenReturn(enabled);
        return preference;
    }

    private SolutionBundleEntity solutionBundle(Long bundleId, Long storeId) {
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getId()).thenReturn(bundleId);
        when(bundle.getStoreId()).thenReturn(storeId);
        return bundle;
    }
}
