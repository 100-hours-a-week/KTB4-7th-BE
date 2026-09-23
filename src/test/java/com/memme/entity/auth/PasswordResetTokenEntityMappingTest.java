package com.memme.entity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class PasswordResetTokenEntityMappingTest {

    @Test
    void erd에_정의된_password_reset_tokens_테이블을_매핑한다() throws Exception {
        Class<?> passwordResetTokenClass = PasswordResetToken.class;

        assertThat(passwordResetTokenClass.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(passwordResetTokenClass.getAnnotation(Table.class).name())
                .isEqualTo("password_reset_tokens");

        assertColumn(passwordResetTokenClass, "tokenHash", "token_hash", 64, false);
        assertColumn(passwordResetTokenClass, "expiresAt", "expires_at", 255, false);
        assertColumn(passwordResetTokenClass, "usedAt", "used_at", 255, true);
        assertColumn(passwordResetTokenClass, "createdAt", "created_at", 255, false);
    }

    @Test
    void 재설정_토큰은_사용자와_user_id_외래키로_연결한다() throws Exception {
        Field user = PasswordResetToken.class.getDeclaredField("user");
        JoinColumn joinColumn = user.getAnnotation(JoinColumn.class);

        assertThat(user.isAnnotationPresent(ManyToOne.class)).isTrue();
        assertThat(joinColumn).isNotNull();
        assertThat(joinColumn.name()).isEqualTo("user_id");
        assertThat(joinColumn.nullable()).isFalse();
    }

    private void assertColumn(
            Class<?> entityClass,
            String fieldName,
            String columnName,
            int length,
            boolean nullable
    ) throws NoSuchFieldException {
        Field field = entityClass.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.length()).isEqualTo(length);
        assertThat(column.nullable()).isEqualTo(nullable);
    }
}
