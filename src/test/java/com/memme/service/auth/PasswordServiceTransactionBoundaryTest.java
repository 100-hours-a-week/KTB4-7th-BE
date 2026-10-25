package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.PasswordChangeRequest;
import com.memme.dto.auth.PasswordResetRequest;
import com.memme.entity.auth.PasswordResetToken;
import com.memme.entity.auth.User;
import com.memme.repository.auth.PasswordResetTokenRepository;
import com.memme.repository.auth.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PasswordServiceTransactionBoundaryTest.TestConfig.class)
class PasswordServiceTransactionBoundaryTest {

    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordChangeService passwordChangeService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private TransactionProbe transactionProbe;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearProbe() {
        transactionProbe.clear();
    }

    @Test
    void 재설정_토큰조회와_BCrypt는_트랜잭션_밖에서_실행한다() {
        LocalDateTime now = LocalDateTime.now();
        User user = User.create("owner@memme.com", "old-hash", "01012345678", now);
        PasswordResetToken token = PasswordResetToken.create(
                user, "a".repeat(64), now.plusMinutes(10), now
        );
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenAnswer(invocation -> {
            transactionProbe.record("reset-token-read");
            return Optional.of(token);
        });
        when(passwordResetTokenRepository.findByIdForUpdate(any())).thenAnswer(invocation -> {
            transactionProbe.record("reset-token-lock-read");
            return Optional.of(token);
        });

        passwordResetService.resetPassword(
                new PasswordResetRequest("raw-token", "NewPassword1!", "NewPassword1!")
        );

        assertThat(transactionProbe.observations())
                .contains(new Observation("reset-token-read", false))
                .contains(new Observation("reset-token-lock-read", true))
                .contains(new Observation("encode", false));
    }

    @Test
    void 비밀번호변경의_matches와_encode는_트랜잭션_밖에서_실행한다() {
        LocalDateTime now = LocalDateTime.now();
        User user = User.create("owner@memme.com", "old-hash", "01012345678", now);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenAnswer(invocation -> {
            transactionProbe.record("user-read");
            return Optional.of(user);
        });
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).thenAnswer(invocation -> {
            transactionProbe.record("user-lock-read");
            return Optional.of(user);
        });

        passwordChangeService.changePassword(
                1L, new PasswordChangeRequest("OldPassword1!", "NewPassword1!", "NewPassword1!")
        );

        assertThat(transactionProbe.observations())
                .contains(new Observation("user-read", false))
                .contains(new Observation("user-lock-read", true))
                .contains(new Observation("matches", false))
                .contains(new Observation("encode", false));
    }

    @Test
    void 외부_트랜잭션이_있어도_비밀번호변경_BCrypt는_트랜잭션_밖에서_실행한다() {
        LocalDateTime now = LocalDateTime.now();
        User user = User.create("owner@memme.com", "old-hash", "01012345678", now);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenAnswer(invocation -> {
            transactionProbe.record("user-read");
            return Optional.of(user);
        });
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).thenAnswer(invocation -> {
            transactionProbe.record("user-lock-read");
            return Optional.of(user);
        });

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

            passwordChangeService.changePassword(
                    1L, new PasswordChangeRequest("OldPassword1!", "NewPassword1!", "NewPassword1!")
            );

            assertThat(transactionProbe.observations())
                    .contains(new Observation("user-read", false))
                    .contains(new Observation("user-lock-read", true))
                    .contains(new Observation("matches", false))
                    .contains(new Observation("encode", false));
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });
    }

    @Test
    void 외부_트랜잭션이_있어도_비밀번호재설정_BCrypt는_트랜잭션_밖에서_실행한다() {
        LocalDateTime now = LocalDateTime.now();
        User user = User.create("owner@memme.com", "old-hash", "01012345678", now);
        PasswordResetToken token = PasswordResetToken.create(
                user, "a".repeat(64), now.plusMinutes(10), now
        );
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenAnswer(invocation -> {
            transactionProbe.record("reset-token-read");
            return Optional.of(token);
        });
        when(passwordResetTokenRepository.findByIdForUpdate(any())).thenAnswer(invocation -> {
            transactionProbe.record("reset-token-lock-read");
            return Optional.of(token);
        });

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

            passwordResetService.resetPassword(
                    new PasswordResetRequest("raw-token", "NewPassword1!", "NewPassword1!")
            );

            assertThat(transactionProbe.observations())
                    .contains(new Observation("reset-token-read", false))
                    .contains(new Observation("reset-token-lock-read", true))
                    .contains(new Observation("encode", false));
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }

        @Bean
        TransactionProbe transactionProbe() {
            return new TransactionProbe();
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        PasswordResetTokenRepository passwordResetTokenRepository() {
            return mock(PasswordResetTokenRepository.class);
        }

        @Bean
        PasswordResetMailSender passwordResetMailSender() {
            return mock(PasswordResetMailSender.class);
        }

        @Bean
        PasswordResetRateLimiter passwordResetRateLimiter() {
            return mock(PasswordResetRateLimiter.class);
        }

        @Bean
        PasswordEncoder passwordEncoder(TransactionProbe transactionProbe) {
            return new PasswordEncoder() {
                @Override
                public String encode(CharSequence rawPassword) {
                    transactionProbe.record("encode");
                    return "new-hash";
                }

                @Override
                public boolean matches(CharSequence rawPassword, String encodedPassword) {
                    transactionProbe.record("matches");
                    return rawPassword.toString().equals("OldPassword1!");
                }
            };
        }

        @Bean
        PasswordResetService passwordResetService(
                UserRepository userRepository,
                PasswordResetTokenRepository passwordResetTokenRepository,
                PasswordResetMailSender passwordResetMailSender,
                PasswordResetRateLimiter passwordResetRateLimiter,
                PasswordEncoder passwordEncoder,
                PlatformTransactionManager transactionManager
        ) {
            return new PasswordResetService(
                    userRepository,
                    passwordResetTokenRepository,
                    passwordResetMailSender,
                    passwordResetRateLimiter,
                    passwordEncoder,
                    "http://localhost:5173/password-reset",
                    transactionManager
            );
        }

        @Bean
        PasswordChangeService passwordChangeService(
                UserRepository userRepository,
                PasswordEncoder passwordEncoder,
                PlatformTransactionManager transactionManager
        ) {
            return new PasswordChangeService(userRepository, passwordEncoder, transactionManager);
        }
    }

    static class TransactionProbe {

        private final List<Observation> observations = new ArrayList<>();

        void record(String operation) {
            observations.add(new Observation(
                    operation, TransactionSynchronizationManager.isActualTransactionActive()
            ));
        }

        List<Observation> observations() {
            return observations;
        }

        void clear() {
            observations.clear();
        }
    }

    record Observation(String operation, boolean transactionActive) {
    }

}
