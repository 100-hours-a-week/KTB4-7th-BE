package com.memme.repository.noti;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class NotificationRepositoryContractTest {

    @Test
    void 사용자와_알림유형과_멱등키로_기존_알림_존재를_확인한다() throws Exception {
        Method existsByUserIdAndNotificationTypeAndNotificationKey = NotificationRepository.class.getMethod(
                "existsByUserIdAndNotificationTypeAndNotificationKey",
                Long.class,
                com.memme.entity.noti.NotificationType.class,
                String.class
        );

        assertThat(existsByUserIdAndNotificationTypeAndNotificationKey.getReturnType()).isEqualTo(boolean.class);
    }
}
