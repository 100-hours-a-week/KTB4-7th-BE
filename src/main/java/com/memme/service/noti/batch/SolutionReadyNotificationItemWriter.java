package com.memme.service.noti.batch;

import com.memme.entity.noti.NotificationType;
import com.memme.repository.noti.NotificationBulkInsertRepository;
import com.memme.repository.noti.NotificationSseQueryRepository.NotificationIdentity;
import com.memme.service.noti.NotificationSsePublicationService;
import java.util.ArrayList;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
public class SolutionReadyNotificationItemWriter implements
        ItemWriter<NotificationBulkInsertRepository.NotificationRow> {

    private final NotificationBulkInsertRepository bulkInsertRepository;
    private final NotificationSsePublicationService publisher;

    public SolutionReadyNotificationItemWriter(
            NotificationBulkInsertRepository bulkInsertRepository,
            NotificationSsePublicationService publisher
    ) {
        this.bulkInsertRepository = bulkInsertRepository;
        this.publisher = publisher;
    }

    @Override
    public void write(Chunk<? extends NotificationBulkInsertRepository.NotificationRow> chunk) {
        if (!chunk.isEmpty()) {
            bulkInsertRepository.insertAll(new ArrayList<>(chunk.getItems()));
            publisher.publishAfterCommit(NotificationType.SOLUTION_READY, chunk.getItems().stream()
                    .map(row -> new NotificationIdentity(row.userId(), row.notificationKey()))
                    .toList());
        }
    }
}
