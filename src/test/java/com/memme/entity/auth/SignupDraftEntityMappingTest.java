package com.memme.entity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class SignupDraftEntityMappingTest {

    @Test
    void erd에_정의된_signup_drafts_테이블을_매핑한다() throws Exception {
        Class<?> signupDraftClass = Class.forName("com.memme.entity.auth.SignupDraft");

        assertThat(signupDraftClass.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(signupDraftClass.getAnnotation(Table.class).name()).isEqualTo("signup_drafts");

        assertColumn(signupDraftClass, "signupTokenHash", "signup_token_hash", 64, false, true);
        assertColumn(signupDraftClass, "email", "email", 100, false, false);
        assertColumn(signupDraftClass, "passwordHash", "password_hash", 255, false, false);
        assertColumn(signupDraftClass, "phone", "phone", 20, false, false);
        assertColumn(signupDraftClass, "termsOfServiceVersion", "terms_of_service_version", 50, false, false);
        assertColumn(signupDraftClass, "privacyPolicyVersion", "privacy_policy_version", 50, false, false);
    }

    private void assertColumn(
            Class<?> entityClass,
            String fieldName,
            String columnName,
            int length,
            boolean nullable,
            boolean unique
    ) throws NoSuchFieldException {
        Field field = entityClass.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.length()).isEqualTo(length);
        assertThat(column.nullable()).isEqualTo(nullable);
        assertThat(column.unique()).isEqualTo(unique);
    }
}
