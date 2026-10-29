package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.entity.auth.User;
import com.memme.entity.noti.FcmDelivery;
import com.memme.entity.noti.FcmToken;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationType;
import com.memme.repository.noti.FcmDeliveryRepository;
import com.memme.repository.noti.FcmTokenRepository;
import com.memme.repository.noti.NotificationBulkInsertRepository.NotificationRow;
import com.memme.repository.noti.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class FcmDeliveryEnqueueServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 8, 0);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final FcmTokenRepository tokenRepository = mock(FcmTokenRepository.class);
    private final FcmDeliveryRepository deliveryRepository = mock(FcmDeliveryRepository.class);
    private final FcmDeliveryEnqueueService service = new FcmDeliveryEnqueueService(
            notificationRepository, tokenRepository, deliveryRepository,
            Clock.fixed(Instant.parse("2026-10-28T23:00:00Z"), ZoneId.of("Asia/Seoul")));

    @Test
    void 저장된_알림과_등록된_토큰마다_발송_대기를_기록한다() {
        Notification notification = notification(1L, 7L, "solution-ready:10");
        FcmToken first = token(notification.getUser(), "hash-1");
        FcmToken second = token(notification.getUser(), "hash-2");
        when(tokenRepository.findAllByUserIdIn(List.of(7L))).thenReturn(List.of(first, second));

        service.enqueueSaved(List.of(notification));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<FcmDelivery>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(deliveryRepository).saveAll(captor.capture());
        List<FcmDelivery> deliveries = ((List<FcmDelivery>) captor.getValue());
        assertThat(deliveries).hasSize(2);
        assertThat(deliveries).extracting(FcmDelivery::getTokenHash).containsExactly("hash-1", "hash-2");
        assertThat(deliveries).allMatch(delivery -> delivery.getStatus() == FcmDelivery.Status.PENDING
                && delivery.getAttemptCount() == 0 && delivery.getNotification() == notification);
    }

    @Test
    void 벌크_저장_알림은_사용자와_알림키가_모두_일치할_때만_발송_대상이_된다() {
        Notification matching = notification(1L, 7L, "solution-ready:10");
        Notification otherUser = notification(2L, 8L, "solution-ready:10");
        Notification otherKey = notification(3L, 7L, "solution-ready:11");
        NotificationRow row = row(7L, "solution-ready:10");
        when(notificationRepository.findAllByUserIdInAndNotificationTypeAndNotificationKeyIn(
                List.of(7L), NotificationType.SOLUTION_READY, List.of("solution-ready:10")))
                .thenReturn(List.of(matching, otherUser, otherKey));
        when(tokenRepository.findAllByUserIdIn(List.of(7L)))
                .thenReturn(List.of(token(matching.getUser(), "hash-1")));

        service.enqueueBulkRows(List.of(row));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<FcmDelivery>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(deliveryRepository).saveAll(captor.capture());
        assertThat((List<FcmDelivery>) captor.getValue()).singleElement()
                .extracting(FcmDelivery::getNotification).isSameAs(matching);
    }

    private Notification notification(Long id, Long userId, String key) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        Notification notification = Notification.create(user, NotificationType.SOLUTION_READY,
                key, "제목", "내용", "SOLUTION_BUNDLE", 10L, NOW, NOW);
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    private FcmToken token(User user, String hash) {
        return FcmToken.register(user, "raw-" + hash, hash, NOW);
    }

    private NotificationRow row(Long userId, String key) {
        return new NotificationRow(userId, NotificationType.SOLUTION_READY, key,
                "제목", "내용", "SOLUTION_BUNDLE", 10L, NOW, NOW);
    }
}
