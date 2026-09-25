package com.memme.dto.noti;

import java.time.OffsetDateTime;

public record NotificationReadResponse(
        int updatedCount,
        OffsetDateTime readAt
) {
}
