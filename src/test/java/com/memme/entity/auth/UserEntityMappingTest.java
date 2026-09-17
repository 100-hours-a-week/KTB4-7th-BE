package com.memme.entity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class UserEntityMappingTest {

    @Test
    void erd에_정의된_users_테이블을_매핑한다() throws Exception {
        Class<?> userClass = Class.forName("com.memme.entity.auth.User");

        assertThat(userClass.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(userClass.getAnnotation(Table.class).name()).isEqualTo("users");

        assertColumn(userClass, "email", "email", 100, false, true);
        assertColumn(userClass, "passwordHash", "password_hash", 255, false, false);
        assertColumn(userClass, "phone", "phone", 20, false, true);
    }

    private void assertColumn(
            Class<?> userClass,
            String fieldName,
            String columnName,
            int length,
            boolean nullable,
            boolean unique
    ) throws NoSuchFieldException {
        Field field = userClass.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.length()).isEqualTo(length);
        assertThat(column.nullable()).isEqualTo(nullable);
        assertThat(column.unique()).isEqualTo(unique);
    }
}
