package com.memme.entity.noti;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NotificationEntityMappingTest {

    @Test
    void 알림을_notifications_테이블과_사용자에게_매핑한다() throws Exception {
        Class<?> notificationClass = Notification.class;

        assertThat(notificationClass.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(notificationClass.getAnnotation(Table.class).name()).isEqualTo("notifications");

        Field user = notificationClass.getDeclaredField("user");
        JoinColumn joinColumn = user.getAnnotation(JoinColumn.class);
        assertThat(user.isAnnotationPresent(ManyToOne.class)).isTrue();
        assertThat(user.getAnnotation(ManyToOne.class).fetch()).isEqualTo(FetchType.LAZY);
        assertThat(user.getType()).isEqualTo(User.class);
        assertThat(joinColumn.name()).isEqualTo("user_id");
        assertThat(joinColumn.nullable()).isFalse();
    }

    @Test
    void 알림_유형과_화면_이동_대상_및_시각을_저장한다() throws Exception {
        Class<?> notificationClass = Notification.class;

        Field notificationType = notificationClass.getDeclaredField("notificationType");
        assertThat(notificationType.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
        assertColumn(notificationClass, "notificationType", "notification_type", false);
        assertColumn(notificationClass, "title", "title", false);
        assertColumn(notificationClass, "content", "content", false);
        assertColumn(notificationClass, "relatedEntityType", "related_entity_type", true);
        assertColumn(notificationClass, "relatedEntityId", "related_entity_id", true);
        assertColumn(notificationClass, "scheduledAt", "scheduled_at", true);
        assertColumn(notificationClass, "sentAt", "sent_at", true);
        assertColumn(notificationClass, "readAt", "read_at", true);

        assertThat(notificationClass.getDeclaredField("sentAt").getType()).isEqualTo(LocalDateTime.class);
        assertThat(NotificationType.values())
                .containsExactly(NotificationType.SOLUTION_READY, NotificationType.SALES_UPLOAD_REMINDER);
    }

    private void assertColumn(
            Class<?> entityClass,
            String fieldName,
            String columnName,
            boolean nullable
    ) throws NoSuchFieldException {
        Column column = entityClass.getDeclaredField(fieldName).getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.nullable()).isEqualTo(nullable);
    }
}
