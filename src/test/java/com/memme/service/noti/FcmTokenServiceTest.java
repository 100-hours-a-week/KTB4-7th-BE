package com.memme.service.noti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.memme.entity.auth.User;
import com.memme.entity.noti.FcmToken;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.FcmTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FcmTokenServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-28T01:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime REGISTERED_AT = LocalDateTime.of(2026, 10, 28, 1, 0);

    @Test
    void 새_토큰을_현재_사용자에게_등록하고_원문_대신_해시로_중복을_찾는다() {
        UserRepository userRepository = mock(UserRepository.class);
        FcmTokenRepository tokenRepository = mock(FcmTokenRepository.class);
        User user = user(17L);
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(17L)).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHashForUpdate(sha256("browser-token"))).thenReturn(Optional.empty());
        when(tokenRepository.save(any(FcmToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FcmTokenService service = new FcmTokenService(userRepository, tokenRepository, FIXED_CLOCK);

        service.registerToken(17L, "browser-token");

        ArgumentCaptor<FcmToken> tokenCaptor = ArgumentCaptor.forClass(FcmToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        FcmToken saved = tokenCaptor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getTokenValue()).isEqualTo("browser-token");
        assertThat(saved.getTokenHash()).isEqualTo(sha256("browser-token"));
        assertThat(saved.getLastRegisteredAt()).isEqualTo(REGISTERED_AT);
        verify(tokenRepository).findByTokenHashForUpdate(sha256("browser-token"));
    }

    @Test
    void 같은_사용자가_같은_토큰을_다시_등록하면_기존_행을_갱신한다() {
        UserRepository userRepository = mock(UserRepository.class);
        FcmTokenRepository tokenRepository = mock(FcmTokenRepository.class);
        User user = user(17L);
        FcmToken existing = FcmToken.register(
                user, "browser-token", sha256("browser-token"), REGISTERED_AT.minusDays(1));
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(17L)).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHashForUpdate(sha256("browser-token"))).thenReturn(Optional.of(existing));
        FcmTokenService service = new FcmTokenService(userRepository, tokenRepository, FIXED_CLOCK);

        service.registerToken(17L, "browser-token");

        assertThat(existing.getLastRegisteredAt()).isEqualTo(REGISTERED_AT);
        assertThat(existing.getTokenValue()).isEqualTo("browser-token");
        verify(tokenRepository, never()).save(any(FcmToken.class));
    }

    @Test
    void 다른_계정에서_같은_브라우저_토큰을_등록하면_현재_사용자에게_연결한다() {
        UserRepository userRepository = mock(UserRepository.class);
        FcmTokenRepository tokenRepository = mock(FcmTokenRepository.class);
        User previousUser = user(17L);
        User currentUser = user(23L);
        FcmToken existing = FcmToken.register(
                previousUser, "browser-token", sha256("browser-token"), REGISTERED_AT.minusDays(1));
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(23L)).thenReturn(Optional.of(currentUser));
        when(tokenRepository.findByTokenHashForUpdate(sha256("browser-token"))).thenReturn(Optional.of(existing));
        FcmTokenService service = new FcmTokenService(userRepository, tokenRepository, FIXED_CLOCK);

        service.registerToken(23L, "browser-token");

        assertThat(existing.getUser()).isSameAs(currentUser);
        assertThat(existing.getLastRegisteredAt()).isEqualTo(REGISTERED_AT);
        verify(tokenRepository, never()).save(any(FcmToken.class));
    }

    @Test
    void 해제는_현재_사용자와_토큰_해시가_일치하는_행만_삭제한다() {
        UserRepository userRepository = mock(UserRepository.class);
        FcmTokenRepository tokenRepository = mock(FcmTokenRepository.class);
        FcmTokenService service = new FcmTokenService(userRepository, tokenRepository, FIXED_CLOCK);

        service.unregisterToken(17L, "browser-token");

        verify(tokenRepository).deleteByUserIdAndTokenHash(17L, sha256("browser-token"));
        verify(tokenRepository, never()).deleteByTokenHash(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void 탈퇴한_사용자나_없는_사용자는_토큰을_등록할_수_없다() {
        UserRepository userRepository = mock(UserRepository.class);
        FcmTokenRepository tokenRepository = mock(FcmTokenRepository.class);
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(17L)).thenReturn(Optional.empty());
        FcmTokenService service = new FcmTokenService(userRepository, tokenRepository, FIXED_CLOCK);

        assertThatThrownBy(() -> service.registerToken(17L, "browser-token"))
                .isInstanceOf(AuthenticationRequiredException.class);

        verifyNoInteractions(tokenRepository);
    }

    private User user(Long userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        return user;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
