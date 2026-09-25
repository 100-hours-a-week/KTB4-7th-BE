package com.memme.dto.noti;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.noti.NotificationType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.lang.reflect.RecordComponent;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 알림_목록_조회_요청은_기본값과_페이지_크기를_검증한다() {
        NotificationListRequest defaultRequest = new NotificationListRequest(null, null, null);

        assertThat(defaultRequest.readStatus()).isEqualTo(NotificationReadStatus.ALL);
        assertThat(defaultRequest.size()).isEqualTo(20);
        assertThat(validator.validate(defaultRequest)).isEmpty();
        assertThat(validator.validate(new NotificationListRequest(NotificationReadStatus.UNREAD, "cursor", 0)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("size");
        assertThat(validator.validate(new NotificationListRequest(NotificationReadStatus.ALL, null, 51)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("size");
    }

    @Test
    void 읽음_처리_요청은_하나부터_백개까지의_양수_ID만_받는다() {
        assertThat(validator.validate(new NotificationReadRequest(List.of(1L, 2L)))).isEmpty();
        assertThat(validator.validate(new NotificationReadRequest(List.of())))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("notificationIds");
        assertThat(validator.validate(new NotificationReadRequest(List.of(0L))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("notificationIds[0].<list element>");
    }

    @Test
    void 알림_목록과_읽음_처리_응답은_API_필드를_가진다() {
        assertThat(componentNames(NotificationListResponse.class))
                .containsExactly("message", "nextCursor", "data");
        assertThat(componentNames(NotificationListResponse.Data.class)).containsExactly("items");
        assertThat(componentNames(NotificationListResponse.Item.class)).containsExactly(
                "id", "type", "title", "content", "relatedEntityType", "relatedEntityId", "sentAt", "readAt"
        );
        assertThat(NotificationListResponse.Item.class.getRecordComponents()[1].getType())
                .isEqualTo(NotificationType.class);
        assertThat(NotificationListResponse.Item.class.getRecordComponents()[6].getType())
                .isEqualTo(OffsetDateTime.class);
        assertThat(componentNames(NotificationReadResponse.class))
                .containsExactly("updatedCount", "readAt");
    }

    private List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
