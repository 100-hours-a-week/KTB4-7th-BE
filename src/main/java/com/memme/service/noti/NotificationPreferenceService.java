package com.memme.service.noti;

import com.memme.dto.noti.NotificationPreferenceResponse;
import com.memme.dto.noti.NotificationPreferenceUpdateRequest;
import com.memme.entity.noti.NotificationPreference;
import com.memme.repository.noti.NotificationPreferenceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final Clock clock;

    public NotificationPreferenceService(
            NotificationPreferenceRepository notificationPreferenceRepository,
            Clock clock
    ) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreferences(Long userId) {
        return toResponse(findPreference(userId));
    }

    @Transactional
    public NotificationPreferenceResponse updatePreferences(Long userId, NotificationPreferenceUpdateRequest request) {
        NotificationPreference preference = findPreference(userId);
        preference.updatePreferences(
                request.solutionEnabled(),
                request.salesUploadReminderEnabled(),
                LocalDateTime.now(clock)
        );

        return toResponse(preference);
    }

    private NotificationPreference findPreference(Long userId) {
        return notificationPreferenceRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("알림 설정 정보를 찾을 수 없습니다."));
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference preference) {
        return new NotificationPreferenceResponse(new NotificationPreferenceResponse.Preferences(
                preference.isSolutionEnabled(),
                preference.isSalesUploadReminderEnabled()
        ));
    }
}
