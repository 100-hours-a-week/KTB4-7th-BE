package com.memme.dto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UserProfileDtoTest {

    @Test
    void 내_정보_조회_응답_DTO는_user_객체를_가진다() {
        assertTrue(UserProfileResponse.class.isRecord());

        assertEquals(
                Arrays.asList("user"),
                Arrays.stream(UserProfileResponse.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }

    @Test
    void 내_정보_조회_사용자_DTO는_아이디_이메일_휴대폰번호_매장명을_가진다() {
        assertTrue(UserProfileResponse.User.class.isRecord());

        assertEquals(
                Arrays.asList("id", "email", "phone", "storeName"),
                Arrays.stream(UserProfileResponse.User.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList()
        );
    }
}
