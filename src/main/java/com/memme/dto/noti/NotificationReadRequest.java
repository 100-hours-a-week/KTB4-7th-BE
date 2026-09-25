package com.memme.dto.noti;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NotificationReadRequest(
        @NotEmpty(message = "읽음 처리할 알림을 선택해 주세요.")
        @Size(max = 100, message = "한 번에 최대 100개의 알림만 읽음 처리할 수 있습니다.")
        List<@Positive(message = "알림 ID는 양수여야 합니다.") Long> notificationIds
) {
}
