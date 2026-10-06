package com.memme.dto.store;

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

class StoreProfileUpdateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 매장_정보_수정_요청_DTO는_API_필드를_가진다() {
        assertTrue(StoreProfileUpdateRequest.class.isRecord());
        assertEquals(
                List.of("storeName", "address", "businessRegNumber", "businessVerificationId", "businessHours"),
                componentNames(StoreProfileUpdateRequest.class)
        );
        assertEquals(
                List.of("postalCode", "roadAddress", "addressDetail"),
                componentNames(StoreProfileUpdateRequest.Address.class)
        );
        assertEquals(
                List.of("dayOfWeek", "isClosed", "openTime", "closeTime"),
                componentNames(StoreProfileUpdateRequest.BusinessHours.class)
        );
    }

    @Test
    void 매장_정보_수정_응답_DTO는_사업자등록번호_없이_수정된_매장_정보를_가진다() {
        assertTrue(StoreProfileUpdateResponse.class.isRecord());
        assertEquals(List.of("store"), componentNames(StoreProfileUpdateResponse.class));
        assertEquals(
                List.of("id", "storeName", "address", "businessHours"),
                componentNames(StoreProfileUpdateResponse.Store.class)
        );
        assertEquals(
                List.of("postalCode", "roadAddress", "addressDetail"),
                componentNames(StoreProfileUpdateResponse.Address.class)
        );
        assertEquals(
                List.of("dayOfWeek", "isClosed", "openTime", "closeTime"),
                componentNames(StoreProfileUpdateResponse.BusinessHours.class)
        );
    }

    @Test
    void 매장명_주소_영업시간은_각각_선택적으로_요청할_수_있다() {
        StoreProfileUpdateRequest request = new StoreProfileUpdateRequest(
                "수정된 매장명", null, null, null, null
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void 매장명은_괄호를_허용하되_십오자를_넘길_수_없다() {
        StoreProfileUpdateRequest branchName = new StoreProfileUpdateRequest(
                "스타벅스(강남점)", null, null, null, null
        );
        StoreProfileUpdateRequest tooLong = new StoreProfileUpdateRequest(
                "가나다라마바사아자차카타파하하거", null, null, null, null
        );

        assertTrue(validator.validate(branchName).isEmpty());
        assertTrue(paths(validator.validate(tooLong)).contains("storeName"));
    }

    @Test
    void 단순_입력값_형식은_Bean_Validation으로_검증한다() {
        StoreProfileUpdateRequest request = new StoreProfileUpdateRequest(
                "맴매@카페",
                new StoreProfileUpdateRequest.Address("1234", "", "상세 주소"),
                "123456789",
                -1L,
                List.of(new StoreProfileUpdateRequest.BusinessHours(DayOfWeek.MONDAY, false, "9:00", "18:00"))
        );

        Set<ConstraintViolation<StoreProfileUpdateRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(paths(violations).contains("storeName"));
        assertTrue(paths(violations).contains("address.postalCode"));
        assertTrue(paths(violations).contains("address.roadAddress"));
        assertTrue(paths(violations).contains("businessHours"));
        assertTrue(paths(violations).contains("businessHours[0].openTime"));
        assertTrue(paths(violations).contains("businessRegNumber"));
        assertTrue(paths(violations).contains("businessVerificationId"));
    }

    private List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private Set<String> paths(Set<ConstraintViolation<StoreProfileUpdateRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
