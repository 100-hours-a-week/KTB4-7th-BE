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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate nonTransactionalTemplate;
    private final TransactionTemplate transactionTemplate;

    public PasswordChangeService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.nonTransactionalTemplate = new TransactionTemplate(transactionManager);
        this.nonTransactionalTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void changePassword(Long userId, PasswordChangeRequest request) {
        nonTransactionalTemplate.executeWithoutResult(
                status -> changePasswordWithoutTransaction(userId, request)
        );
    }

    private void changePasswordWithoutTransaction(Long userId, PasswordChangeRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(AuthenticationRequiredException::new);
        String originalPasswordHash = user.getPasswordHash();

        if (!passwordEncoder.matches(request.currentPassword(), originalPasswordHash)) {
            throw new InvalidCurrentPasswordException();
        }
        if (passwordEncoder.matches(request.newPassword(), originalPasswordHash)) {
            throw new InvalidPasswordChangeRequestException(List.of(
                    new FieldError("newPassword", "현재 비밀번호와 다르게 입력해 주세요.")
            ));
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new InvalidPasswordChangeRequestException(List.of(
                    new FieldError("confirmPassword", "비밀번호와 일치하지 않습니다.")
            ));
        }

        String newPasswordHash = passwordEncoder.encode(request.newPassword());
        transactionTemplate.executeWithoutResult(status -> {
            User userToUpdate = userRepository.findByIdAndDeletedAtIsNullForUpdate(userId)
                    .orElseThrow(AuthenticationRequiredException::new);
            if (!originalPasswordHash.equals(userToUpdate.getPasswordHash())) {
                throw new InvalidCurrentPasswordException();
            }

            userToUpdate.changePassword(newPasswordHash, LocalDateTime.now());
        });
    }
}
