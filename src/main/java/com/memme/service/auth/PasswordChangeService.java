package com.memme.service.auth;

import com.memme.dto.auth.PasswordChangeRequest;
import com.memme.dto.common.FieldError;
import com.memme.entity.auth.User;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.exception.auth.InvalidCurrentPasswordException;
import com.memme.exception.auth.InvalidPasswordChangeRequestException;
import com.memme.repository.auth.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordChangeService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(AuthenticationRequiredException::new);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordChangeRequestException(List.of(
                    new FieldError("newPassword", "현재 비밀번호와 다르게 입력해 주세요.")
            ));
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new InvalidPasswordChangeRequestException(List.of(
                    new FieldError("confirmPassword", "비밀번호와 일치하지 않습니다.")
            ));
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()), LocalDateTime.now());
    }
}
