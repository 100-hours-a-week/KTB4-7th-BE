package com.memme.dto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.RecordComponent;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SignupBusinessDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 회원가입_2단계_요청_DTO는_API_필드를_가진다() {
        assertTrue(SignupBusinessRequest.class.isRecord());
        assertEquals(
                Arrays.asList(
                        "storeName",
                        "businessRegNumber",
                        "businessVerificationId",
                        "postalCode",
                        "address",
                        "addressDetail",
                        "businessHours"
                ),
                Arrays.stream(SignupBusinessRequest.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }

    @Test
    void 회원가입_2단계_응답_DTO는_사용자_매장_다음화면_정보를_가진다() {
        assertTrue(SignupBusinessResponse.class.isRecord());
        assertEquals(
                Arrays.asList("user", "store", "next"),
                Arrays.stream(SignupBusinessResponse.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
        assertEquals(
                Arrays.asList("id", "email"),
                Arrays.stream(SignupBusinessResponse.User.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
        assertEquals(
                Arrays.asList("id", "storeName"),
                Arrays.stream(SignupBusinessResponse.Store.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }

    @Test
    void 단순_입력값_형식은_Bean_Validation으로_검증한다() {
        SignupBusinessRequest request = new SignupBusinessRequest(
                "",
                "123",
                0L,
                "1234",
                "",
                "상세 주소",
                List.of(new SignupBusinessRequest.BusinessHours(DayOfWeek.MONDAY, false, "9:00", "18:00"))
        );

        Set<ConstraintViolation<SignupBusinessRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().map(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.equals("storeName")));
        assertTrue(violations.stream().map(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.equals("businessRegNumber")));
        assertTrue(violations.stream().map(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.equals("businessVerificationId")));
        assertTrue(violations.stream().map(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.equals("postalCode")));
        assertTrue(violations.stream().map(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.equals("address")));
        assertTrue(violations.stream().map(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.equals("businessHours[0].openTime")));
    }

    @Test
    void 매장명은_요구사항의_허용문자와_최대_십오자_규칙을_검증한다() {
        SignupBusinessRequest invalidCharacter = validRequest("맴매@카페");
        SignupBusinessRequest tooLong = validRequest("가나다라마바사아자차카타파하하거");

        assertTrue(validator.validate(invalidCharacter).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.equals("storeName")));
        assertTrue(validator.validate(tooLong).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.equals("storeName")));
    }

    @Test
    void 영업시간은_월요일부터_일요일까지_일곱건을_요청한다() {
        SignupBusinessRequest request = new SignupBusinessRequest(
                "맴매카페",
                "1234567890",
                1L,
                "06236",
                "서울특별시 강남구 테헤란로 123",
                null,
                List.of(new SignupBusinessRequest.BusinessHours(DayOfWeek.MONDAY, false, "09:00", "18:00"))
        );

        Set<ConstraintViolation<SignupBusinessRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().map(violation -> violation.getPropertyPath().toString())
                .anyMatch(path -> path.equals("businessHours")));
    }

    private SignupBusinessRequest validRequest(String storeName) {
        return new SignupBusinessRequest(
                storeName,
                "1234567890",
                1L,
                "06236",
                "서울특별시 강남구 테헤란로 123",
                null,
                List.of(
                        new SignupBusinessRequest.BusinessHours(DayOfWeek.MONDAY, false, "09:00", "18:00"),
                        new SignupBusinessRequest.BusinessHours(DayOfWeek.TUESDAY, false, "09:00", "18:00"),
                        new SignupBusinessRequest.BusinessHours(DayOfWeek.WEDNESDAY, false, "09:00", "18:00"),
                        new SignupBusinessRequest.BusinessHours(DayOfWeek.THURSDAY, false, "09:00", "18:00"),
                        new SignupBusinessRequest.BusinessHours(DayOfWeek.FRIDAY, false, "09:00", "18:00"),
                        new SignupBusinessRequest.BusinessHours(DayOfWeek.SATURDAY, false, "09:00", "18:00"),
                        new SignupBusinessRequest.BusinessHours(DayOfWeek.SUNDAY, false, "09:00", "18:00")
                )
        );
    }
}
