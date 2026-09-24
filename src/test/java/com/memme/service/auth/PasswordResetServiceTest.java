package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.PasswordResetEmailRequest;
import com.memme.dto.auth.PasswordResetRequest;
import com.memme.entity.auth.PasswordResetToken;
import com.memme.entity.auth.User;
import com.memme.exception.auth.InvalidPasswordResetRequestException;
import com.memme.exception.auth.PasswordResetTokenExpiredException;
import com.memme.repository.auth.PasswordResetTokenRepository;
import com.memme.repository.auth.UserRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String EMAIL = "owner@memme.com";
    private static final String FRONTEND_RESET_URL = "http://localhost:5173/password-reset";

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordResetMailSender passwordResetMailSender;
    @Mock private PasswordEncoder passwordEncoder;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                passwordResetMailSender,
                passwordEncoder,
                FRONTEND_RESET_URL
        );
    }

    @Test
    void 가입된_이메일이면_원문_토큰은_메일로_전달하고_해시와_30분_만료시각을_저장한다() throws Exception {
        User user = user();
        when(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(user));
        LocalDateTime beforeRequest = LocalDateTime.now();

        passwordResetService.requestResetEmail(new PasswordResetEmailRequest(EMAIL));

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        verify(passwordResetMailSender).send(org.mockito.ArgumentMatchers.eq(EMAIL), linkCaptor.capture());

        String rawToken = linkCaptor.getValue().substring((FRONTEND_RESET_URL + "?token=").length());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        LocalDateTime expiresAt = field(savedToken, "expiresAt");

        assertThat(linkCaptor.getValue()).startsWith(FRONTEND_RESET_URL + "?token=");
        assertThat(rawToken).isNotBlank();
        String savedTokenHash = field(savedToken, "tokenHash");
        assertThat(savedTokenHash).isNotEqualTo(rawToken);
        assertThat(expiresAt).isBetween(beforeRequest.plusMinutes(30), LocalDateTime.now().plusMinutes(30));
    }

    @Test
    void 가입되지_않은_이메일이어도_동일하게_처리하고_토큰과_메일을_만들지_않는다() {
        when(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.empty());

        passwordResetService.requestResetEmail(new PasswordResetEmailRequest(EMAIL));

        verify(passwordResetTokenRepository, never()).save(any());
        verifyNoInteractions(passwordResetMailSender);
    }

    @Test
    void 유효한_토큰과_일치하는_비밀번호이면_비밀번호를_암호화하고_토큰을_사용처리한다() {
        User user = user();
        PasswordResetToken token = PasswordResetToken.create(
                user,
                "a".repeat(64),
                LocalDateTime.now().plusMinutes(1),
                LocalDateTime.now()
        );
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("encoded-new-password");

        passwordResetService.resetPassword(new PasswordResetRequest(
                "raw-token", "NewPassword1!", "NewPassword1!"
        ));

        assertThat(user.getPasswordHash()).isEqualTo("encoded-new-password");
        assertThat(token.isUsed()).isTrue();
        verify(passwordEncoder).encode("NewPassword1!");
    }

    @Test
    void 비밀번호_확인이_일치하지_않으면_입력오류_예외를_던지고_토큰을_사용하지_않는다() {
        assertThatThrownBy(() -> passwordResetService.resetPassword(new PasswordResetRequest(
                "raw-token", "NewPassword1!", "DifferentPassword1!"
        )))
                .isInstanceOf(InvalidPasswordResetRequestException.class);

        verifyNoInteractions(passwordResetTokenRepository);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void 만료되었거나_이미_사용한_토큰은_410_예외를_던진다() {
        PasswordResetToken expiredToken = PasswordResetToken.create(
                user(),
                "a".repeat(64),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().minusMinutes(31)
        );
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> passwordResetService.resetPassword(new PasswordResetRequest(
                "raw-token", "NewPassword1!", "NewPassword1!"
        )))
                .isInstanceOf(PasswordResetTokenExpiredException.class);

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void 이미_사용한_토큰은_410_예외를_던진다() {
        PasswordResetToken usedToken = PasswordResetToken.create(
                user(),
                "a".repeat(64),
                LocalDateTime.now().plusMinutes(1),
                LocalDateTime.now()
        );
        usedToken.markUsedAt(LocalDateTime.now());
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(usedToken));

        assertThatThrownBy(() -> passwordResetService.resetPassword(new PasswordResetRequest(
                "raw-token", "NewPassword1!", "NewPassword1!"
        )))
                .isInstanceOf(PasswordResetTokenExpiredException.class);

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void 존재하지_않는_토큰은_410_예외를_던진다() {
        when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(new PasswordResetRequest(
                "raw-token", "NewPassword1!", "NewPassword1!"
        )))
                .isInstanceOf(PasswordResetTokenExpiredException.class);

        verify(passwordEncoder, never()).encode(any());
    }

    private User user() {
        return User.create(EMAIL, "encoded-password", "01012345678", LocalDateTime.now());
    }

    @SuppressWarnings("unchecked")
    private <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }
}
