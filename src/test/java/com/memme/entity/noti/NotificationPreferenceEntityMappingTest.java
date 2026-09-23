package com.memme.entity.noti;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NotificationPreferenceEntityMappingTest {

    @Test
    void 사용자별_기본_알림_설정을_notification_preferences_테이블에_매핑한다() throws Exception {
        Class<?> preferenceClass = NotificationPreference.class;

        assertThat(preferenceClass.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(preferenceClass.getAnnotation(Table.class).name()).isEqualTo("notification_preferences");

        Field user = preferenceClass.getDeclaredField("user");
        JoinColumn joinColumn = user.getAnnotation(JoinColumn.class);
        assertThat(user.isAnnotationPresent(OneToOne.class)).isTrue();
        assertThat(joinColumn.name()).isEqualTo("user_id");
        assertThat(joinColumn.nullable()).isFalse();
        assertThat(joinColumn.unique()).isTrue();

        assertBooleanColumn(preferenceClass, "solutionEnabled", "solution_enabled");
        assertBooleanColumn(preferenceClass, "salesUploadReminderEnabled", "sales_upload_reminder_enabled");
        assertThat(preferenceClass.getDeclaredField("createdAt").getType()).isEqualTo(LocalDateTime.class);
        assertThat(preferenceClass.getDeclaredField("updatedAt").getType()).isEqualTo(LocalDateTime.class);
        assertThat(preferenceClass.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("rankingChangeEnabled");
    }

    @Test
    void 모든_알림_수신값은_회원가입_시_false로_초기화된다() throws Exception {
        Class<?> preferenceClass = NotificationPreference.class;
        Constructor<?> constructor = preferenceClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object preference = constructor.newInstance();

        assertThat(readBoolean(preferenceClass, preference, "solutionEnabled")).isFalse();
        assertThat(readBoolean(preferenceClass, preference, "salesUploadReminderEnabled")).isFalse();
    }

    private void assertBooleanColumn(Class<?> entityClass, String fieldName, String columnName) throws NoSuchFieldException {
        Column column = entityClass.getDeclaredField(fieldName).getAnnotation(Column.class);

        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.nullable()).isFalse();
    }

    private boolean readBoolean(Class<?> entityClass, Object target, String fieldName) throws Exception {
        Field field = entityClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }
}
