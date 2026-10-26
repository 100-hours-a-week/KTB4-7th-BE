package com.memme.service.noti.batch;

import com.memme.repository.noti.NotificationBulkInsertRepository;
import java.util.ArrayList;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
public class SolutionReadyNotificationItemWriter implements
        ItemWriter<NotificationBulkInsertRepository.NotificationRow> {

    private final NotificationBulkInsertRepository bulkInsertRepository;

    public SolutionReadyNotificationItemWriter(NotificationBulkInsertRepository bulkInsertRepository) {
        this.bulkInsertRepository = bulkInsertRepository;
    }

    @Override
    public void write(Chunk<? extends NotificationBulkInsertRepository.NotificationRow> chunk) {
        if (!chunk.isEmpty()) {
            bulkInsertRepository.insertAll(new ArrayList<>(chunk.getItems()));
        }
    }
}
