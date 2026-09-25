package com.memme.dto.noti;

public record NotificationPreferenceResponse(
        Preferences preferences
) {

    public record Preferences(
            boolean solutionEnabled,
            boolean salesUploadReminderEnabled
    ) {
    }
}
