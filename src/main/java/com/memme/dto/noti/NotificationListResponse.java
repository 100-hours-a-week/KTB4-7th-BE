package com.memme.dto.noti;

import com.memme.entity.noti.NotificationType;
import java.time.OffsetDateTime;
import java.util.List;

public record NotificationListResponse(
        String message,
        String nextCursor,
        Data data
) {

    public record Data(
            List<Item> items
    ) {
    }

    public record Item(
            Long id,
            NotificationType type,
            String title,
            String content,
            String relatedEntityType,
            Long relatedEntityId,
            OffsetDateTime sentAt,
            OffsetDateTime readAt
    ) {
    }
}
