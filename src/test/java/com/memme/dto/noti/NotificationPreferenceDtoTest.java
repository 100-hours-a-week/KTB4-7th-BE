package com.memme.dto.noti;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationPreferenceDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 알림_설정_조회_응답_DTO는_V1_토글_두개를_반환한다() {
        assertTrue(NotificationPreferenceResponse.class.isRecord());
        assertEquals(List.of("preferences"), componentNames(NotificationPreferenceResponse.class));
        assertEquals(
                List.of("solutionEnabled", "salesUploadReminderEnabled"),
                componentNames(NotificationPreferenceResponse.Preferences.class)
        );
    }

    @Test
    void 알림_설정_수정_요청은_토글_하나만_전달해도_유효하다() {
        NotificationPreferenceUpdateRequest request = new NotificationPreferenceUpdateRequest(true, null);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void 알림_설정_수정_요청은_토글을_하나도_전달하지_않으면_유효하지_않다() {
        NotificationPreferenceUpdateRequest request = new NotificationPreferenceUpdateRequest(null, null);

        assertFalse(validator.validate(request).isEmpty());
    }

    private List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
