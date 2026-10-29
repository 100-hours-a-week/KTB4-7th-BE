package com.memme.service.noti.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.memme.entity.noti.NotificationType;
import com.memme.repository.noti.NotificationBulkInsertRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;

class SolutionReadyNotificationItemProcessingTest {

    @Test
    void 후보를_솔루션_도착_알림_행으로_변환한다() throws Exception {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-24T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        SolutionReadyNotificationItemProcessor processor = new SolutionReadyNotificationItemProcessor(clock);

        NotificationBulkInsertRepository.NotificationRow row = processor.process(
                new SolutionReadyNotificationCandidate(101L, 99L, "solution-ready:99"));

        assertThat(row.userId()).isEqualTo(101L);
        assertThat(row.notificationType()).isEqualTo(NotificationType.SOLUTION_READY);
        assertThat(row.notificationKey()).isEqualTo("solution-ready:99");
        assertThat(row.title()).isEqualTo("새 솔루션이 준비되었습니다.");
        assertThat(row.content()).isEqualTo("오늘의 솔루션을 확인해 주세요.");
        assertThat(row.relatedEntityType()).isEqualTo("SOLUTION_BUNDLE");
        assertThat(row.relatedEntityId()).isEqualTo(99L);
        assertThat(row.sentAt()).isEqualTo(LocalDateTime.of(2026, 10, 24, 9, 0));
        assertThat(row.createdAt()).isEqualTo(row.sentAt());
    }

    @Test
    void writer는_청크의_알림들을_벌크_저장소에_한번_전달한다() throws Exception {
        NotificationBulkInsertRepository bulkInsertRepository = mock(NotificationBulkInsertRepository.class);
        SolutionReadyNotificationItemWriter writer = new SolutionReadyNotificationItemWriter(
                bulkInsertRepository, mock(com.memme.service.noti.FcmDeliveryEnqueueService.class));
        NotificationBulkInsertRepository.NotificationRow first = row(1L);
        NotificationBulkInsertRepository.NotificationRow second = row(2L);

        writer.write(new Chunk<>(List.of(first, second)));

        verify(bulkInsertRepository).insertAll(List.of(first, second));
    }

    @Test
    void 빈_청크는_저장소를_호출하지_않는다() throws Exception {
        NotificationBulkInsertRepository bulkInsertRepository = mock(NotificationBulkInsertRepository.class);
        SolutionReadyNotificationItemWriter writer = new SolutionReadyNotificationItemWriter(
                bulkInsertRepository, mock(com.memme.service.noti.FcmDeliveryEnqueueService.class));

        writer.write(new Chunk<>());

        verify(bulkInsertRepository, never()).insertAll(org.mockito.ArgumentMatchers.anyList());
    }

    private NotificationBulkInsertRepository.NotificationRow row(Long bundleId) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 24, 9, 0);
        return new NotificationBulkInsertRepository.NotificationRow(
                bundleId,
                NotificationType.SOLUTION_READY,
                "solution-ready:%d".formatted(bundleId),
                "새 솔루션이 준비되었습니다.",
                "오늘의 솔루션을 확인해 주세요.",
                "SOLUTION_BUNDLE",
                bundleId,
                now,
                now
        );
    }
}
