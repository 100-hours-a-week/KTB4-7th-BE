package com.memme.dto.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AddressSearchDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 주소_검색_요청_DTO는_정의된_쿼리_파라미터를_가진다() throws Exception {
        Class<?> requestType = Class.forName("com.memme.dto.store.AddressSearchRequest");

        assertTrue(requestType.isRecord());
        assertEquals(
                Arrays.asList("query", "cursor", "size"),
                Arrays.stream(requestType.getRecordComponents()).map(RecordComponent::getName).toList()
        );
    }

    @Test
    void 주소_검색_응답_DTO는_주소목록과_다음_커서를_가진다() throws Exception {
        Class<?> responseType = Class.forName("com.memme.dto.store.AddressSearchResponse");

        assertTrue(responseType.isRecord());
        assertEquals(
                Arrays.asList("addresses", "nextCursor"),
                Arrays.stream(responseType.getRecordComponents()).map(RecordComponent::getName).toList()
        );
    }

    @Test
    void 커서는_비어_있거나_일_이상의_정수_문자열만_허용한다() {
        assertTrue(validator.validate(new AddressSearchRequest("판교역로", null, null)).isEmpty());
        assertTrue(validator.validate(new AddressSearchRequest("판교역로", "", null)).isEmpty());
        assertTrue(validator.validate(new AddressSearchRequest("판교역로", "1", null)).isEmpty());

        assertCursorValidationFails("0");
        assertCursorValidationFails("-1");
        assertCursorValidationFails("abc");
    }

    private void assertCursorValidationFails(String cursor) {
        Set<ConstraintViolation<AddressSearchRequest>> violations = validator.validate(
                new AddressSearchRequest("판교역로", cursor, null)
        );

        assertEquals(1, violations.size());
        assertEquals("cursor", violations.iterator().next().getPropertyPath().toString());
    }
}
