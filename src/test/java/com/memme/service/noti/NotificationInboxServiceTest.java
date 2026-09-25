package com.memme.service.noti;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.memme.dto.noti.NotificationListRequest;
import com.memme.dto.noti.NotificationListResponse;
import com.memme.dto.noti.NotificationReadRequest;
import com.memme.dto.noti.NotificationReadResponse;
import com.memme.dto.noti.NotificationReadStatus;
import com.memme.entity.auth.User;
import com.memme.entity.noti.Notification;
import com.memme.entity.noti.NotificationType;
import com.memme.exception.noti.NotificationNotFoundException;
import com.memme.exception.noti.InvalidNotificationCursorException;
import com.memme.repository.noti.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationInboxServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 알림을_최신순_커서_기반으로_조회한다() {
        NotificationRepository repository = Mockito.mock(NotificationRepository.class);
        Notification first = notification(3L, LocalDateTime.of(2026, 9, 25, 9, 0));
        Notification second = notification(2L, LocalDateTime.of(2026, 9, 25, 8, 0));
        Notification third = notification(1L, LocalDateTime.of(2026, 9, 25, 7, 0));
        when(repository.findAllByUserIdOrderBySentAtDesc(1L)).thenReturn(List.of(first, second, third));
        NotificationInboxService service = new NotificationInboxService(repository, FIXED_CLOCK);

        NotificationListResponse firstPage = service.getNotifications(
                1L, new NotificationListRequest(NotificationReadStatus.ALL, null, 1)
        );
        NotificationListResponse secondPage = service.getNotifications(
                1L, new NotificationListRequest(NotificationReadStatus.ALL, firstPage.nextCursor(), 1)
        );

        assertEquals(3L, firstPage.data().items().getFirst().id());
        assertNotNull(firstPage.nextCursor());
        assertEquals(2L, secondPage.data().items().getFirst().id());
    }

    @Test
    void 본인_미읽음_알림만_읽음_처리한다() {
        NotificationRepository repository = Mockito.mock(NotificationRepository.class);
        Notification unreadNotification = notification(10L, LocalDateTime.of(2026, 9, 25, 9, 0));
        when(repository.findAllById(any())).thenReturn(List.of(unreadNotification));
        NotificationInboxService service = new NotificationInboxService(repository, FIXED_CLOCK);

        NotificationReadResponse response = service.markAsRead(1L, new NotificationReadRequest(List.of(10L)));

        assertEquals(1, response.updatedCount());
        verify(unreadNotification).markAsRead(LocalDateTime.now(FIXED_CLOCK));
    }

    @Test
    void 타인_알림이나_없는_알림을_읽음_처리하면_찾을수없음_예외를_던진다() {
        NotificationRepository repository = Mockito.mock(NotificationRepository.class);
        Notification notification = notification(10L, LocalDateTime.now(FIXED_CLOCK));
        when(repository.findAllById(any())).thenReturn(List.of(notification));
        NotificationInboxService service = new NotificationInboxService(repository, FIXED_CLOCK);

        assertThrows(
                NotificationNotFoundException.class,
                () -> service.markAsRead(1L, new NotificationReadRequest(List.of(10L, 20L)))
        );
    }

    @Test
    void 형식이_올바르지_않은_커서는_입력_예외를_던진다() {
        NotificationRepository repository = Mockito.mock(NotificationRepository.class);
        NotificationInboxService service = new NotificationInboxService(repository, FIXED_CLOCK);

        assertThrows(
                InvalidNotificationCursorException.class,
                () -> service.getNotifications(1L, new NotificationListRequest(NotificationReadStatus.ALL, "invalid", 20))
        );
    }

    private Notification notification(Long id, LocalDateTime sentAt) {
        User user = Mockito.mock(User.class);
        when(user.getId()).thenReturn(1L);
        Notification notification = Mockito.mock(Notification.class);
        when(notification.getId()).thenReturn(id);
        when(notification.getUser()).thenReturn(user);
        when(notification.getNotificationType()).thenReturn(NotificationType.SOLUTION_READY);
        when(notification.getTitle()).thenReturn("새 솔루션이 준비되었습니다.");
        when(notification.getContent()).thenReturn("오늘의 솔루션을 확인해 주세요.");
        when(notification.getSentAt()).thenReturn(sentAt);
        return notification;
    }
}
