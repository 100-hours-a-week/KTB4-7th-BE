package com.memme.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.SignupBusinessRequest;
import com.memme.dto.auth.SignupBusinessResponse;
import com.memme.entity.auth.SignupDraft;
import com.memme.entity.auth.User;
import com.memme.entity.noti.NotificationPreference;
import com.memme.entity.store.BusinessVerification;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.exception.auth.InvalidSignupRequestException;
import com.memme.exception.auth.SignupCompletionExpiredException;
import com.memme.exception.store.BusinessStatusNotEligibleException;
import com.memme.repository.auth.SignupDraftRepository;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.store.BusinessVerificationRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignupBusinessServiceTest {

    private static final String SIGNUP_TOKEN = "signup-token";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 10, 0);

    @Mock private SignupDraftRepository signupDraftRepository;
    @Mock private BusinessVerificationRepository businessVerificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private StoreBusinessHoursRepository storeBusinessHoursRepository;
    @Mock private NotificationPreferenceRepository notificationPreferenceRepository;

    private SignupBusinessService service;

    @BeforeEach
    void setUp() {
        service = new SignupBusinessService(
                signupDraftRepository, businessVerificationRepository, userRepository, storeRepository,
                storeBusinessHoursRepository, notificationPreferenceRepository,
                Clock.fixed(Instant.parse("2026-09-21T01:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
    }

    @Test
    void 유효한_요청이면_사용자_매장_영업시간_알림설정을_저장하고_인증결과를_사용처리한다() throws Exception {
        SignupDraft draft = draft(NOW.plusHours(1));
        BusinessVerification verification = verification(NOW.plusMinutes(10));
        when(signupDraftRepository.findBySignupTokenHash(any())).thenReturn(Optional.of(draft));
        when(businessVerificationRepository.findById(1L)).thenReturn(Optional.of(verification));
        when(storeRepository.existsByBusinessRegistrationNo("1234567890")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));
        when(storeRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 20L));

        SignupBusinessResponse response = service.completeSignup(SIGNUP_TOKEN, validRequest());

        assertEquals(10L, response.user().id());
        assertEquals("owner@memme.com", response.user().email());
        assertEquals(20L, response.store().id());
        assertEquals("맴매카페", response.store().storeName());
        assertEquals("LOGIN", response.next());
        verify(storeBusinessHoursRepository).saveAll(any());
        verify(notificationPreferenceRepository).save(any(NotificationPreference.class));
        assertEquals(NOW, fieldValue(verification, "usedAt"));
    }

    @Test
    void 임시가입정보가_만료되면_410_예외를_던지고_저장하지_않는다() {
        when(signupDraftRepository.findBySignupTokenHash(any())).thenReturn(Optional.of(draft(NOW)));

        assertThrows(SignupCompletionExpiredException.class, () -> service.completeSignup(SIGNUP_TOKEN, validRequest()));

        verify(userRepository, never()).save(any());
    }

    @Test
    void 사업자_인증_결과가_다른_번호면_422_예외를_던진다() {
        when(signupDraftRepository.findBySignupTokenHash(any())).thenReturn(Optional.of(draft(NOW.plusHours(1))));
        when(businessVerificationRepository.findById(1L)).thenReturn(Optional.of(
                BusinessVerification.create("9999999999", NOW, NOW.plusMinutes(10), NOW)
        ));

        assertThrows(BusinessStatusNotEligibleException.class, () -> service.completeSignup(SIGNUP_TOKEN, validRequest()));

        verify(userRepository, never()).save(any());
    }

    @Test
    void 영업시간이_10분_단위가_아니면_422_예외를_던진다() {
        SignupBusinessRequest request = new SignupBusinessRequest(
                "맴매카페", "1234567890", 1L, "06236", "서울특별시 강남구 테헤란로 123", "101호",
                List.of(new SignupBusinessRequest.BusinessHours(java.time.DayOfWeek.MONDAY, false, "09:05", "18:00"))
        );
        when(signupDraftRepository.findBySignupTokenHash(any())).thenReturn(Optional.of(draft(NOW.plusHours(1))));
        when(businessVerificationRepository.findById(1L)).thenReturn(Optional.of(verification(NOW.plusMinutes(10))));
        when(storeRepository.existsByBusinessRegistrationNo("1234567890")).thenReturn(false);

        InvalidSignupRequestException exception = assertThrows(
                InvalidSignupRequestException.class, () -> service.completeSignup(SIGNUP_TOKEN, request)
        );

        assertEquals("businessHours", exception.getFieldErrors().getFirst().field());
        verify(userRepository, never()).save(any());
    }

    private SignupBusinessRequest validRequest() {
        return new SignupBusinessRequest(
                "맴매카페", "1234567890", 1L, "06236", "서울특별시 강남구 테헤란로 123", "101호",
                List.of(
                        hours(java.time.DayOfWeek.MONDAY), hours(java.time.DayOfWeek.TUESDAY), hours(java.time.DayOfWeek.WEDNESDAY),
                        hours(java.time.DayOfWeek.THURSDAY), hours(java.time.DayOfWeek.FRIDAY), hours(java.time.DayOfWeek.SATURDAY),
                        hours(java.time.DayOfWeek.SUNDAY)
                )
        );
    }

    private SignupBusinessRequest.BusinessHours hours(java.time.DayOfWeek dayOfWeek) {
        return new SignupBusinessRequest.BusinessHours(dayOfWeek, false, "09:00", "18:00");
    }

    private SignupDraft draft(LocalDateTime expiresAt) {
        return SignupDraft.create("hash", "owner@memme.com", "encoded-password", "01012345678", true, "v1", true, "v1", expiresAt, NOW);
    }

    private BusinessVerification verification(LocalDateTime expiresAt) {
        return BusinessVerification.create("1234567890", NOW.minusMinutes(1), expiresAt, NOW.minusMinutes(1));
    }

    private <T> T withId(T target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
        return target;
    }

    private Object fieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
