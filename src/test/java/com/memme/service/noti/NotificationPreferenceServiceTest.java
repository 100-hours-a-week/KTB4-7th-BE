package com.memme.service.noti;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.memme.dto.noti.NotificationPreferenceResponse;
import com.memme.dto.noti.NotificationPreferenceUpdateRequest;
import com.memme.entity.noti.NotificationPreference;
import com.memme.repository.noti.NotificationPreferenceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationPreferenceServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 사용자_알림_설정을_조회한다() {
        NotificationPreferenceRepository repository = Mockito.mock(NotificationPreferenceRepository.class);
        NotificationPreference preference = NotificationPreference.create(null, LocalDateTime.now(FIXED_CLOCK));
        when(repository.findByUserId(1L)).thenReturn(Optional.of(preference));
        NotificationPreferenceService service = new NotificationPreferenceService(repository, FIXED_CLOCK);

        NotificationPreferenceResponse response = service.getPreferences(1L);

        assertFalse(response.preferences().solutionEnabled());
        assertFalse(response.preferences().salesUploadReminderEnabled());
    }

    @Test
    void 전달된_알림_설정만_수정한다() {
        NotificationPreferenceRepository repository = Mockito.mock(NotificationPreferenceRepository.class);
        NotificationPreference preference = NotificationPreference.create(null, LocalDateTime.now(FIXED_CLOCK));
        when(repository.findByUserId(1L)).thenReturn(Optional.of(preference));
        NotificationPreferenceService service = new NotificationPreferenceService(repository, FIXED_CLOCK);

        NotificationPreferenceResponse response = service.updatePreferences(
                1L, new NotificationPreferenceUpdateRequest(true, null)
        );

        assertTrue(response.preferences().solutionEnabled());
        assertFalse(response.preferences().salesUploadReminderEnabled());
    }
}
