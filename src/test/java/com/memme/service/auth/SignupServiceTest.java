package com.memme.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.SignupAccountRequest;
import com.memme.dto.auth.SignupAccountResponse;
import com.memme.entity.auth.SignupDraft;
import com.memme.exception.DuplicateSignupException;
import com.memme.exception.InvalidSignupRequestException;
import com.memme.repository.auth.SignupDraftRepository;
import com.memme.repository.auth.UserRepository;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-09-17T01:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private SignupDraftRepository signupDraftRepository;

    private SignupService signupService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, KOREA_ZONE);
        signupService = new SignupService(
                userRepository,
                signupDraftRepository,
                new BCryptPasswordEncoder(),
                clock
        );
    }

    @Test
    void 유효한_회원가입_정보를_임시_저장하고_한시간_토큰을_발급한다() throws Exception {
        SignupAccountRequest request = validRequest();
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByPhone(request.phone())).thenReturn(false);

        SignupAccountResponse response = signupService.signupAccount(request);

        assertNotNull(response.signupToken());
        assertFalse(response.signupToken().isBlank());
        assertEquals(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), KOREA_ZONE), response.expiresAt());

        ArgumentCaptor<SignupDraft> draftCaptor = ArgumentCaptor.forClass(SignupDraft.class);
        verify(signupDraftRepository).save(draftCaptor.capture());
        SignupDraft savedDraft = draftCaptor.getValue();

        assertEquals(request.email(), fieldValue(savedDraft, "email"));
        assertEquals(request.phone(), fieldValue(savedDraft, "phone"));
        assertEquals(
                sha256(response.signupToken()),
                fieldValue(savedDraft, "signupTokenHash")
        );
        String passwordHash = (String) fieldValue(savedDraft, "passwordHash");
        assertNotEquals(request.password(), passwordHash);
        assertTruePasswordMatches(request.password(), passwordHash);
        assertEquals(true, fieldValue(savedDraft, "termsOfServiceAgreed"));
        assertEquals("2026-09", fieldValue(savedDraft, "termsOfServiceVersion"));
        assertEquals(true, fieldValue(savedDraft, "privacyPolicyAgreed"));
        assertEquals("2026-09", fieldValue(savedDraft, "privacyPolicyVersion"));
    }

    @Test
    void 이미_가입된_이메일이면_중복_예외를_던진다() {
        SignupAccountRequest request = validRequest();
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThrows(DuplicateSignupException.class, () -> signupService.signupAccount(request));

        verify(signupDraftRepository, never()).save(any());
    }

    @Test
    void 이미_가입된_휴대폰_번호면_중복_예외를_던진다() {
        SignupAccountRequest request = validRequest();
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByPhone(request.phone())).thenReturn(true);

        assertThrows(DuplicateSignupException.class, () -> signupService.signupAccount(request));

        verify(signupDraftRepository, never()).save(any());
    }

    @Test
    void 이메일_형식이_올바르지_않으면_검증_예외를_던진다() {
        SignupAccountRequest request = new SignupAccountRequest(
                "owner-memme.com", "Password1!", "Password1!", "01012345678", validAgreements()
        );

        assertThrows(InvalidSignupRequestException.class, () -> signupService.signupAccount(request));

        verify(signupDraftRepository, never()).save(any());
    }

    @Test
    void 비밀번호_정책을_만족하지_않으면_검증_예외를_던진다() {
        SignupAccountRequest request = new SignupAccountRequest(
                "owner@memme.com", "password1!", "password1!", "01012345678", validAgreements()
        );

        assertThrows(InvalidSignupRequestException.class, () -> signupService.signupAccount(request));

        verify(signupDraftRepository, never()).save(any());
    }

    @Test
    void 비밀번호_확인이_일치하지_않으면_검증_예외를_던진다() {
        SignupAccountRequest request = new SignupAccountRequest(
                "owner@memme.com", "Password1!", "Password2!", "01012345678", validAgreements()
        );

        assertThrows(InvalidSignupRequestException.class, () -> signupService.signupAccount(request));

        verify(signupDraftRepository, never()).save(any());
    }

    @Test
    void 휴대폰_번호가_010으로_시작하는_열한자리가_아니면_검증_예외를_던진다() {
        SignupAccountRequest request = new SignupAccountRequest(
                "owner@memme.com", "Password1!", "Password1!", "01112345678", validAgreements()
        );

        assertThrows(InvalidSignupRequestException.class, () -> signupService.signupAccount(request));

        verify(signupDraftRepository, never()).save(any());
    }

    @Test
    void 필수_약관에_동의하지_않으면_검증_예외를_던진다() {
        SignupAccountRequest request = new SignupAccountRequest(
                "owner@memme.com",
                "Password1!",
                "Password1!",
                "01012345678",
                new SignupAccountRequest.Agreements(false, "2026-09", true, "2026-09")
        );

        assertThrows(InvalidSignupRequestException.class, () -> signupService.signupAccount(request));

        verify(signupDraftRepository, never()).save(any());
    }

    @Test
    void 필수_약관_버전이_비어있으면_검증_예외를_던진다() {
        SignupAccountRequest request = new SignupAccountRequest(
                "owner@memme.com",
                "Password1!",
                "Password1!",
                "01012345678",
                new SignupAccountRequest.Agreements(true, "", true, "2026-09")
        );

        assertThrows(InvalidSignupRequestException.class, () -> signupService.signupAccount(request));

        verify(signupDraftRepository, never()).save(any());
    }

    private SignupAccountRequest validRequest() {
        return new SignupAccountRequest(
                "owner@memme.com", "Password1!", "Password1!", "01012345678", validAgreements()
        );
    }

    private SignupAccountRequest.Agreements validAgreements() {
        return new SignupAccountRequest.Agreements(true, "2026-09", true, "2026-09");
    }

    private Object fieldValue(SignupDraft signupDraft, String fieldName) throws Exception {
        Field field = SignupDraft.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(signupDraft);
    }

    private String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private void assertTruePasswordMatches(String rawPassword, String passwordHash) {
        assertEquals(true, BCrypt.checkpw(rawPassword, passwordHash));
    }
}
