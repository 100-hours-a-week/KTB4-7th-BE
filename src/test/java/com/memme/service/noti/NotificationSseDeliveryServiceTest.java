package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.noti.NotificationSseEvent;
import com.memme.entity.noti.NotificationType;
import com.memme.repository.noti.NotificationSseQueryRepository.NotificationIdentity;
import com.memme.repository.noti.NotificationSseQueryRepository.StoredNotification;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationSseDeliveryServiceTest {

    @Test
    void 커밋된_알림을_조회해_해당_사용자에게만_공통_이벤트를_보낸다() {
        NotificationSseStoredNotificationReader reader = mock(NotificationSseStoredNotificationReader.class);
        NotificationSseConnectionService connections = mock(NotificationSseConnectionService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        NotificationSseDeliveryService service = new NotificationSseDeliveryService(reader, connections, clock);
        List<NotificationIdentity> identities = List.of(new NotificationIdentity(7L, "sales-upload:1"));
        LocalDateTime sentAt = LocalDateTime.of(2026, 10, 26, 9, 0);
        when(reader.find(NotificationType.SALES_UPLOAD_REMINDER, identities))
                .thenReturn(List.of(new StoredNotification(12L, 7L, NotificationType.SALES_UPLOAD_REMINDER,
                        "sales-upload:1", "제목", "내용", "SALES_UPLOAD", 1L, sentAt, null)));

        service.deliver(NotificationType.SALES_UPLOAD_REMINDER, identities);

        ArgumentCaptor<NotificationSseEvent> event = ArgumentCaptor.forClass(NotificationSseEvent.class);
        verify(connections).sendToUser(eq(7L), event.capture());
        assertThat(event.getValue().name()).isEqualTo("notification");
        assertThat(event.getValue().data().id()).isEqualTo(12L);
        assertThat(event.getValue().data().sentAt()).isEqualTo(sentAt.atZone(clock.getZone()).toOffsetDateTime());
    }

    @Test
    void 저장된_행이_없으면_이벤트를_보내지_않는다() {
        NotificationSseStoredNotificationReader reader = mock(NotificationSseStoredNotificationReader.class);
        NotificationSseConnectionService connections = mock(NotificationSseConnectionService.class);
        NotificationSseDeliveryService service = new NotificationSseDeliveryService(reader, connections,
                Clock.systemUTC());

        service.deliver(NotificationType.RANKING_CHANGED, List.of(new NotificationIdentity(7L, "rank:1")));

        org.mockito.Mockito.verify(connections, org.mockito.Mockito.never()).sendToUser(any(), any());
    }

}
