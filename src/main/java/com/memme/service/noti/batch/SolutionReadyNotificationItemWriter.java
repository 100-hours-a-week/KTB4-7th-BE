package com.memme.service.noti.batch;

import com.memme.repository.noti.NotificationBulkInsertRepository;
import com.memme.service.noti.FcmDeliveryEnqueueService;
import java.util.ArrayList;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
public class SolutionReadyNotificationItemWriter implements
        ItemWriter<NotificationBulkInsertRepository.NotificationRow> {

    private final NotificationBulkInsertRepository bulkInsertRepository;
    private final FcmDeliveryEnqueueService fcmDeliveryEnqueueService;

    public SolutionReadyNotificationItemWriter(
            NotificationBulkInsertRepository bulkInsertRepository,
            FcmDeliveryEnqueueService fcmDeliveryEnqueueService
    ) {
        this.bulkInsertRepository = bulkInsertRepository;
        this.fcmDeliveryEnqueueService = fcmDeliveryEnqueueService;
    }

    @Override
    public void write(Chunk<? extends NotificationBulkInsertRepository.NotificationRow> chunk) {
        if (!chunk.isEmpty()) {
            ArrayList<NotificationBulkInsertRepository.NotificationRow> rows = new ArrayList<>(chunk.getItems());
            bulkInsertRepository.insertAll(rows);
            fcmDeliveryEnqueueService.enqueueBulkRows(rows);
        }
    }
}
