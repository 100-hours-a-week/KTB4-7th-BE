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
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.noti.NotificationRepository;
import com.memme.repository.sales.SalesForecastRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.store.StoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SalesUploadReminderServiceTest {

    private StoreRepository storeRepository;
    private SalesUploadRepository salesUploadRepository;
    private SalesForecastRepository salesForecastRepository;
    private NotificationPreferenceRepository preferenceRepository;
    private NotificationRepository notificationRepository;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        storeRepository = mock(StoreRepository.class);
        salesUploadRepository = mock(SalesUploadRepository.class);
        salesForecastRepository = mock(SalesForecastRepository.class);
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
    }

    @Test
    void 예측_시작월_말일에_사전_알림을_생성한다() {
        SalesUploadReminderService service = serviceAt("2026-09-30T08:00:00Z");
        givenReminderEnabledStore(10L, 3L, 100L, "2026-10-05");
        User user = mock(User.class);
        when(userRepository.getReferenceById(3L)).thenReturn(user);

        service.sendDailyReminders();

        Notification notification = savedNotification();
        assertThat(notification.getNotificationKey()).isEqualTo("sales-upload:100:PREPARE:2026-09-30");
        assertThat(notification.getTitle()).isEqualTo("다음 매출 데이터를 미리 준비해주세요");
        assertThat(notification.getContent())
                .isEqualTo("예측이 끊기지 않도록 다음 달에도 매출 파일을 업로드해주세요");
    }

    @Test
    void 예측_종료일에_가까워지면_주의_리마인더를_생성한다() {
        SalesUploadReminderService service = serviceAt("2026-10-03T08:00:00Z");
        givenReminderEnabledStore(10L, 3L, 100L, "2026-10-05");
        User user = mock(User.class);
        when(userRepository.getReferenceById(3L)).thenReturn(user);

        service.sendDailyReminders();

        Notification notification = savedNotification();
        assertThat(notification.getNotificationKey()).isEqualTo("sales-upload:100:REMINDER:2026-10-03");
        assertThat(notification.getTitle()).isEqualTo("매출 데이터 업로드가 필요해요");
    }

    @Test
    void 리마인더_수신_설정이_꺼져_있으면_알림을_생성하지_않는다() {
        SalesUploadReminderService service = serviceAt("2026-10-01T08:00:00Z");
        StoreRepository.ActiveStoreOwner storeOwner = storeOwner(10L, 3L);
        when(storeRepository.findAllActiveStoreOwners()).thenReturn(List.of(storeOwner));
        NotificationPreference preference = mock(NotificationPreference.class);
        when(preference.isSalesUploadReminderEnabled()).thenReturn(false);
        when(preferenceRepository.findByUserId(3L)).thenReturn(Optional.of(preference));

        service.sendDailyReminders();

        verify(salesUploadRepository, never()).findFirstByStoreIdAndStatusOrderByUploadedAtDesc(any(), any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void 같은_날짜에_이미_생성된_알림은_중복_생성하지_않는다() {
        SalesUploadReminderService service = serviceAt("2026-10-01T08:00:00Z");
        givenReminderEnabledStore(10L, 3L, 100L, "2026-10-05");
        when(notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(
                any(), any(), any()
        )).thenReturn(true);

        service.sendDailyReminders();

        verify(notificationRepository, never()).save(any());
        verify(userRepository, never()).getReferenceById(any());
    }

    private SalesUploadReminderService serviceAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul"));
        return new SalesUploadReminderService(
                storeRepository,
                salesUploadRepository,
                salesForecastRepository,
                preferenceRepository,
                notificationRepository,
                userRepository,
                clock
        );
    }

    private void givenReminderEnabledStore(
            Long storeId,
            Long ownerUserId,
            Long uploadId,
            String forecastEndDate
    ) {
        StoreRepository.ActiveStoreOwner storeOwner = storeOwner(storeId, ownerUserId);
        when(storeRepository.findAllActiveStoreOwners()).thenReturn(List.of(storeOwner));
        NotificationPreference preference = mock(NotificationPreference.class);
        when(preference.isSalesUploadReminderEnabled()).thenReturn(true);
        when(preferenceRepository.findByUserId(ownerUserId)).thenReturn(Optional.of(preference));
        SalesUploadEntity upload = mock(SalesUploadEntity.class);
        when(upload.getId()).thenReturn(uploadId);
        when(salesUploadRepository.findFirstByStoreIdAndStatusOrderByUploadedAtDesc(any(), any()))
                .thenReturn(Optional.of(upload));
        when(salesForecastRepository.findForecastEndDateByStoreIdAndBasedOnUploadId(storeId, uploadId))
                .thenReturn(Optional.of(java.time.LocalDate.parse(forecastEndDate)));
        when(notificationRepository.existsByUserIdAndNotificationTypeAndNotificationKey(any(), any(), any()))
                .thenReturn(false);
    }

    private StoreRepository.ActiveStoreOwner storeOwner(Long storeId, Long ownerUserId) {
        StoreRepository.ActiveStoreOwner storeOwner = mock(StoreRepository.ActiveStoreOwner.class);
        when(storeOwner.getStoreId()).thenReturn(storeId);
        when(storeOwner.getOwnerUserId()).thenReturn(ownerUserId);
        return storeOwner;
    }

    private Notification savedNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }
}
