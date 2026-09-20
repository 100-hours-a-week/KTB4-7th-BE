package com.memme.dto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BusinessVerificationDtoTest {

    @Test
    void 사업자등록번호_인증_요청_DTO는_API_필드를_가진다() {
        assertTrue(BusinessVerificationRequest.class.isRecord());

        assertEquals(
                Arrays.asList("businessRegNumber"),
                Arrays.stream(BusinessVerificationRequest.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }

    @Test
    void 사업자등록번호_인증_응답_DTO는_인증결과_ID와_만료시각을_가진다() {
        assertTrue(BusinessVerificationResponse.class.isRecord());

        assertEquals(
                Arrays.asList("businessVerificationId", "expiresAt"),
                Arrays.stream(BusinessVerificationResponse.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );

        assertEquals(Long.class, BusinessVerificationResponse.class.getRecordComponents()[0].getType());
        assertEquals(OffsetDateTime.class, BusinessVerificationResponse.class.getRecordComponents()[1].getType());
    }
}
