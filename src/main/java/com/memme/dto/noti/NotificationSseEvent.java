package com.memme.dto.noti;

import java.util.Objects;

/**
 * SSE notification frame metadata and payload. Send {@link #data()} as the SSE data;
 * the payload uses the same shape as an item in the notification list API.
 */
public record NotificationSseEvent(
        String name,
        NotificationListResponse.Item data
) {

    public static final String EVENT_NAME = "notification";

    public NotificationSseEvent(NotificationListResponse.Item data) {
        this(EVENT_NAME, data);
    }

    public NotificationSseEvent {
        if (!EVENT_NAME.equals(name)) {
            throw new IllegalArgumentException("Unsupported notification SSE event name");
        }
        Objects.requireNonNull(data, "data");
    }
}
