package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.memme.entity.noti.NotificationType;
import com.memme.repository.noti.NotificationRepository;
import com.memme.repository.noti.NotificationSseQueryRepository.NotificationIdentity;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationSseStoredNotificationReaderTest {

    @Test
    void 저장된_알림은_백개씩_나눠_조회한다() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationSseStoredNotificationReader reader = new NotificationSseStoredNotificationReader(repository);
        List<NotificationIdentity> identities = IntStream.rangeClosed(1, 101)
                .mapToObj(id -> new NotificationIdentity((long) id, "key-" + id))
                .toList();

        reader.find(NotificationType.SOLUTION_READY, identities);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationIdentity>> lookups = ArgumentCaptor.forClass(List.class);
        verify(repository, times(2)).findSseNotificationsByIdentitiesAndType(lookups.capture(),
                eq(NotificationType.SOLUTION_READY));
        assertThat(lookups.getAllValues()).extracting(List::size).containsExactly(100, 1);
    }
}
