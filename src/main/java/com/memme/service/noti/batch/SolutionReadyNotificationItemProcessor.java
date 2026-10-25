package com.memme.service.noti.batch;

import com.memme.entity.noti.NotificationType;
import com.memme.repository.noti.NotificationBulkInsertRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class SolutionReadyNotificationItemProcessor implements
        ItemProcessor<SolutionReadyNotificationCandidate, NotificationBulkInsertRepository.NotificationRow> {

    private static final String NOTIFICATION_TITLE = "새 솔루션이 준비되었습니다.";
    private static final String NOTIFICATION_CONTENT = "오늘의 솔루션을 확인해 주세요.";
    private static final String RELATED_ENTITY_TYPE = "SOLUTION_BUNDLE";

    private final Clock clock;

    public SolutionReadyNotificationItemProcessor(Clock clock) {
        this.clock = clock;
    }

    @Override
    public NotificationBulkInsertRepository.NotificationRow process(SolutionReadyNotificationCandidate candidate) {
        LocalDateTime now = LocalDateTime.now(clock);
        return new NotificationBulkInsertRepository.NotificationRow(
                candidate.ownerUserId(),
                NotificationType.SOLUTION_READY,
                candidate.notificationKey(),
                NOTIFICATION_TITLE,
                NOTIFICATION_CONTENT,
                RELATED_ENTITY_TYPE,
                candidate.bundleId(),
                now,
                now
        );
    }
}
