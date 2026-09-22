package com.memme.dto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LoginDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 이메일과_비밀번호_형식은_Bean_Validation으로_검증한다() {
        LoginRequest request = new LoginRequest("invalid-email", "");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void 로그인_요청_DTO는_이메일과_비밀번호_필드를_가진다() {
        assertTrue(LoginRequest.class.isRecord());

        assertEquals(
                Arrays.asList("email", "password"),
                Arrays.stream(LoginRequest.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }

    @Test
    void 로그인_응답_DTO는_사용자_ID와_이메일을_가진다() {
        assertTrue(LoginResponse.class.isRecord());
        assertTrue(LoginResponse.User.class.isRecord());

        assertEquals(
                Arrays.asList("user"),
                Arrays.stream(LoginResponse.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
        assertEquals(
                Arrays.asList("id", "email"),
                Arrays.stream(LoginResponse.User.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }
}
