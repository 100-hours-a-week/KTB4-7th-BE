package com.memme.dto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class SignupAccountDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 이메일_휴대폰_비밀번호_약관_버전_형식은_Bean_Validation으로_검증한다() {
        SignupAccountRequest request = new SignupAccountRequest(
                "invalid-email",
                "password1!",
                "different",
                "01112345678",
                new SignupAccountRequest.Agreements(true, "", true, "")
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void 약관_객체가_없으면_Bean_Validation으로_검증한다() {
        SignupAccountRequest request = new SignupAccountRequest(
                "owner@memme.com", "Password1!", "Password1!", "01012345678", null
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void 회원가입_1단계_요청_DTO는_API_필드를_가진다() {
        assertTrue(SignupAccountRequest.class.isRecord());

        assertEquals(
                Arrays.asList("email", "password", "passwordConfirm", "phone", "agreements"),
                Arrays.stream(SignupAccountRequest.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }

    @Test
    void 회원가입_1단계_응답_DTO는_토큰과_만료시각을_가진다() {
        assertTrue(SignupAccountResponse.class.isRecord());

        assertEquals(
                Arrays.asList("signupToken", "expiresAt"),
                Arrays.stream(SignupAccountResponse.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );

        assertEquals(OffsetDateTime.class, SignupAccountResponse.class.getRecordComponents()[1].getType());
    }

    @Test
    void 약관_동의_DTO는_API_필드를_가진다() {
        assertTrue(SignupAccountRequest.Agreements.class.isRecord());

        assertEquals(
                Arrays.asList(
                        "termsOfService",
                        "termsOfServiceVersion",
                        "privacyPolicy",
                        "privacyPolicyVersion"
                ),
                Arrays.stream(SignupAccountRequest.Agreements.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }
}
