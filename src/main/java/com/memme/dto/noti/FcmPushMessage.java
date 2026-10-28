package com.memme.dto.noti;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record FcmPushMessage(
        String token,
        NotificationPayload notification,
        Map<String, String> data
) {

    public FcmPushMessage {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(notification, "notification");
        data = Map.copyOf(Objects.requireNonNull(data, "data"));
    }

    public static FcmPushMessage from(String token, NotificationListResponse.Item item) {
        Objects.requireNonNull(item, "item");
        Map<String, String> data = new LinkedHashMap<>();
        data.put("notificationId", Objects.requireNonNull(item.id(), "notificationId").toString());
        data.put("type", Objects.requireNonNull(item.type(), "type").name());
        if (item.relatedEntityType() != null) {
            data.put("relatedEntityType", item.relatedEntityType());
        }
        if (item.relatedEntityId() != null) {
            data.put("relatedEntityId", item.relatedEntityId().toString());
        }
        return new FcmPushMessage(token, new NotificationPayload(item.title(), item.content()), data);
    }

    @Override
    public String toString() {
        return "FcmPushMessage[token=<redacted>, notification=" + notification + ", data=" + data + "]";
    }

    public record NotificationPayload(String title, String body) {
    }
}
