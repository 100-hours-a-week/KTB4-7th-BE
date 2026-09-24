package com.memme.entity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserEntityMappingTest {

    @Test
    void erd에_정의된_users_테이블을_매핑한다() throws Exception {
        Class<?> userClass = User.class;

        assertThat(userClass.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(userClass.getAnnotation(Table.class).name()).isEqualTo("users");

        assertColumn(userClass, "email", "email", 100, false, true);
        assertColumn(userClass, "passwordHash", "password_hash", 255, false, false);
        assertColumn(userClass, "phone", "phone", 20, false, true);
    }

    @Test
    void 로그인_비밀번호_검증을_위해_비밀번호_해시를_조회할_수_있다() {
        User user = User.create(
                "owner@memme.com",
                "encoded-password",
                "01012345678",
                LocalDateTime.of(2026, 9, 22, 20, 0)
        );

        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
    }

    @Test
    void 내_정보_조회에_필요한_휴대폰_번호를_조회할_수_있다() {
        User user = User.create(
                "owner@memme.com",
                "encoded-password",
                "01012345678",
                LocalDateTime.of(2026, 9, 22, 20, 0)
        );

        assertThat(user.getPhone()).isEqualTo("01012345678");
    }

    @Test
    void 회원_탈퇴_시각을_기록할_수_있다() throws Exception {
        User user = User.create(
                "owner@memme.com",
                "encoded-password",
                "01012345678",
                LocalDateTime.of(2026, 9, 22, 20, 0)
        );
        LocalDateTime withdrawnAt = LocalDateTime.of(2026, 9, 22, 21, 0);

        user.withdraw(withdrawnAt);

        Field field = User.class.getDeclaredField("deletedAt");
        field.setAccessible(true);
        assertThat(field.get(user)).isEqualTo(withdrawnAt);
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
