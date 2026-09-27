package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SolutionReadyNotificationServiceTest {

    private StoreRepository storeRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private NotificationRepository notificationRepository;
    private UserRepository userRepository;
    private SolutionBundleRepository solutionBundleRepository;
    private Clock clock;
    private SolutionReadyNotificationService service;

    @BeforeEach
    void setUp() {
        storeRepository = mock(StoreRepository.class);
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        solutionBundleRepository = mock(SolutionBundleRepository.class);
        clock = Clock.fixed(Instant.parse("2026-09-26T00:00:00Z"), ZoneOffset.UTC);
        service = new SolutionReadyNotificationService(
                storeRepository,
                preferenceRepository,
                notificationRepository,
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
        givenEnabledOwner(10L, 3L);
        givenEnabledOwner(11L, 4L);
        when(userRepository.getReferenceById(any())).thenReturn(mock(User.class));
        when(notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(any(), any(), any()))
                .thenReturn(false);

        service.notifyTodaysSolutions();

        verify(notificationRepository, org.mockito.Mockito.times(2)).save(any());
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

    private SolutionBundleEntity solutionBundle(Long bundleId, Long storeId) {
        SolutionBundleEntity bundle = mock(SolutionBundleEntity.class);
        when(bundle.getId()).thenReturn(bundleId);
        when(bundle.getStoreId()).thenReturn(storeId);
        return bundle;
    }
}
