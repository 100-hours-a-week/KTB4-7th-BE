package com.memme.dto.noti;

import jakarta.validation.constraints.AssertTrue;

public record NotificationPreferenceUpdateRequest(
        Boolean solutionEnabled,
        Boolean salesUploadReminderEnabled
) {

    @AssertTrue(message = "수정할 알림 설정을 하나 이상 입력해 주세요.")
    public boolean hasPreferenceToUpdate() {
        return solutionEnabled != null || salesUploadReminderEnabled != null;
    }
}
