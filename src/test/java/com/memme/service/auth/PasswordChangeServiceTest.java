package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.PasswordChangeRequest;
import com.memme.entity.auth.User;
import com.memme.exception.InvalidCurrentPasswordException;
import com.memme.exception.InvalidPasswordChangeRequestException;
import com.memme.repository.auth.UserRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordChangeServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private PasswordChangeService passwordChangeService;

    @BeforeEach
    void setUp() {
        passwordChangeService = new PasswordChangeService(userRepository, passwordEncoder);
    }

    @Test
    void 현재_비밀번호가_일치하고_새_비밀번호가_다르면_새_해시로_변경한다() throws Exception {
        User user = user(1L, "old-encoded-password");
        PasswordChangeRequest request = new PasswordChangeRequest(
                "OldPassword1!", "NewPassword1!", "NewPassword1!"
        );
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword1!", "old-encoded-password")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword1!", "old-encoded-password")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("new-encoded-password");

        passwordChangeService.changePassword(1L, request);

        assertThat(user.getPasswordHash()).isEqualTo("new-encoded-password");
        verify(passwordEncoder).encode("NewPassword1!");
    }

    @Test
    void 현재_비밀번호가_일치하지_않으면_현재_비밀번호_오류를_던진다() throws Exception {
        User user = user(1L, "old-encoded-password");
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword1!", "old-encoded-password")).thenReturn(false);

        assertThrows(InvalidCurrentPasswordException.class, () -> passwordChangeService.changePassword(
                1L, new PasswordChangeRequest("WrongPassword1!", "NewPassword1!", "NewPassword1!")
        ));
    }

    @Test
    void 새_비밀번호가_현재_비밀번호와_같으면_새_비밀번호_fieldError를_던진다() throws Exception {
        User user = user(1L, "old-encoded-password");
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword1!", "old-encoded-password")).thenReturn(true);
        when(passwordEncoder.matches("OldPassword1!", "old-encoded-password")).thenReturn(true);

        InvalidPasswordChangeRequestException exception = assertThrows(
                InvalidPasswordChangeRequestException.class,
                () -> passwordChangeService.changePassword(
                        1L, new PasswordChangeRequest("OldPassword1!", "OldPassword1!", "OldPassword1!")
                )
        );

        assertThat(exception.getFieldErrors().getFirst().field()).isEqualTo("newPassword");
        assertThat(exception.getFieldErrors().getFirst().message()).isEqualTo("현재 비밀번호와 다르게 입력해 주세요.");
    }

    @Test
    void 새_비밀번호_확인값이_일치하지_않으면_확인값_fieldError를_던진다() throws Exception {
        User user = user(1L, "old-encoded-password");
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword1!", "old-encoded-password")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword1!", "old-encoded-password")).thenReturn(false);

        InvalidPasswordChangeRequestException exception = assertThrows(
                InvalidPasswordChangeRequestException.class,
                () -> passwordChangeService.changePassword(
                        1L, new PasswordChangeRequest("OldPassword1!", "NewPassword1!", "DifferentPassword1!")
                )
        );

        assertThat(exception.getFieldErrors().getFirst().field()).isEqualTo("confirmPassword");
    }

    private User user(Long id, String passwordHash) throws Exception {
        User user = User.create(
                "owner@memme.com", passwordHash, "01012345678", LocalDateTime.of(2026, 9, 24, 18, 0)
        );
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
        return user;
    }
}
