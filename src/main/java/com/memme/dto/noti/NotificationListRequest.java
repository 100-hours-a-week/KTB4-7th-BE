package com.memme.dto.noti;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record NotificationListRequest(
        NotificationReadStatus readStatus,
        String cursor,
        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 50, message = "size는 50 이하여야 합니다.")
        Integer size
) {

    public NotificationListRequest {
        readStatus = readStatus == null ? NotificationReadStatus.ALL : readStatus;
        size = size == null ? 20 : size;
    }
}
