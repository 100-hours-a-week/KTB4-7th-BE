package com.memme.dto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PasswordChangeDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 현재_비밀번호와_확인값은_필수이고_새_비밀번호는_정책을_검증한다() {
        PasswordChangeRequest request = new PasswordChangeRequest("", "password", "");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void 비밀번호_수정_요청_DTO는_현재_비밀번호와_새_비밀번호와_확인값을_가진다() {
        assertTrue(PasswordChangeRequest.class.isRecord());

        assertEquals(
                Arrays.asList("currentPassword", "newPassword", "confirmPassword"),
                Arrays.stream(PasswordChangeRequest.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }
}
