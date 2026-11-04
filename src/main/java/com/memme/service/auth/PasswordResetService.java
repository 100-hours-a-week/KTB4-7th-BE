package com.memme.service.auth;

import com.memme.dto.auth.PasswordResetEmailRequest;
import com.memme.dto.auth.PasswordResetRequest;
import com.memme.dto.common.FieldError;
import com.memme.entity.auth.PasswordResetToken;
import com.memme.exception.auth.InvalidPasswordResetRequestException;
import com.memme.exception.auth.PasswordResetTokenExpiredException;
import com.memme.repository.auth.PasswordResetTokenRepository;
import com.memme.repository.auth.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordResetService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final long TOKEN_EXPIRATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordResetMailSender passwordResetMailSender;
    private final PasswordResetRateLimiter passwordResetRateLimiter;
    private final PasswordHashConcurrencyGate passwordHashConcurrencyGate;
    private final String passwordResetUrl;
    private final TransactionTemplate nonTransactionalTemplate;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordResetMailSender passwordResetMailSender,
            PasswordResetRateLimiter passwordResetRateLimiter,
            PasswordHashConcurrencyGate passwordHashConcurrencyGate,
            @Value("${password-reset.url}") String passwordResetUrl,
            PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordResetMailSender = passwordResetMailSender;
        this.passwordResetRateLimiter = passwordResetRateLimiter;
        this.passwordHashConcurrencyGate = passwordHashConcurrencyGate;
        this.passwordResetUrl = passwordResetUrl;
        this.nonTransactionalTemplate = new TransactionTemplate(transactionManager);
        this.nonTransactionalTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void requestResetEmail(PasswordResetEmailRequest request, String clientIp) {
        passwordResetRateLimiter.recordIfAllowed(request.email(), clientIp);
        userRepository.findByEmailAndDeletedAtIsNull(request.email()).ifPresent(user -> {
            LocalDateTime now = LocalDateTime.now();
            String rawToken = generateRawToken();
            String tokenHash = hash(rawToken);
            PasswordResetToken passwordResetToken = PasswordResetToken.create(
                    user,
                    tokenHash,
                    now.plusMinutes(TOKEN_EXPIRATION_MINUTES),
                    now
            );

            passwordResetTokenRepository.save(passwordResetToken);
            passwordResetMailSender.send(user.getEmail(), passwordResetUrl + "?token=" + rawToken);
        });
    }

    public void resetPassword(PasswordResetRequest request) {
        nonTransactionalTemplate.executeWithoutResult(status -> resetPasswordWithoutTransaction(request));
    }

    private void resetPasswordWithoutTransaction(PasswordResetRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new InvalidPasswordResetRequestException(List.of(
                    new FieldError("confirmPassword", "비밀번호와 일치하지 않습니다.")
            ));
        }

        String tokenHash = hash(request.token());
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(PasswordResetTokenExpiredException::new);
        if (passwordResetToken.isExpiredAt(LocalDateTime.now()) || passwordResetToken.isUsed()) {
            throw new PasswordResetTokenExpiredException();
        }

        String newPasswordHash = passwordHashConcurrencyGate.encode(request.newPassword());
        transactionTemplate.executeWithoutResult(status -> {
            PasswordResetToken tokenToUpdate = passwordResetTokenRepository.findByIdForUpdate(passwordResetToken.getId())
                    .orElseThrow(PasswordResetTokenExpiredException::new);
            LocalDateTime now = LocalDateTime.now();
            if (tokenToUpdate.isExpiredAt(now) || tokenToUpdate.isUsed()) {
                throw new PasswordResetTokenExpiredException();
            }

            tokenToUpdate.getUser().changePassword(newPasswordHash, now);
            tokenToUpdate.markUsedAt(now);
        });
    }

    private String generateRawToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
