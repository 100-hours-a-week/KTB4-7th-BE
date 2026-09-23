package com.memme.dto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PasswordResetDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 비밀번호_재설정_이메일_요청은_이메일_형식을_검증한다() {
        PasswordResetEmailRequest request = new PasswordResetEmailRequest("invalid-email");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void 비밀번호_재설정_요청은_토큰과_새_비밀번호_형식을_검증한다() {
        PasswordResetRequest request = new PasswordResetRequest("", "password1!", "");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void 비밀번호_재설정_이메일_요청_DTO는_이메일_필드를_가진다() {
        assertTrue(PasswordResetEmailRequest.class.isRecord());

        assertEquals(
                Arrays.asList("email"),
                Arrays.stream(PasswordResetEmailRequest.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }

    @Test
    void 비밀번호_재설정_요청_DTO는_API_필드를_가진다() {
        assertTrue(PasswordResetRequest.class.isRecord());

        assertEquals(
                Arrays.asList("token", "newPassword", "confirmPassword"),
                Arrays.stream(PasswordResetRequest.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }
}
