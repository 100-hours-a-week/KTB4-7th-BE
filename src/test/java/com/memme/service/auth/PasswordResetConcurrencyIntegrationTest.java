package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.PasswordResetRequest;
import com.memme.entity.auth.PasswordResetToken;
import com.memme.entity.auth.User;
import com.memme.exception.auth.PasswordResetTokenExpiredException;
import com.memme.repository.auth.PasswordResetTokenRepository;
import com.memme.repository.auth.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class PasswordResetConcurrencyIntegrationTest {

    @Autowired private PasswordResetService passwordResetService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @MockitoBean private PasswordEncoder passwordEncoder;

    @Test
    void 같은_재설정_토큰으로_동시에_요청하면_한_번만_성공한다() throws Exception {
        String rawToken = "concurrent-reset-token";
        String tokenHash = sha256(rawToken);
        LocalDateTime now = LocalDateTime.now();
        User user = userRepository.saveAndFlush(
                User.create("reset-concurrency@example.com", "old-hash", "01012345681", now)
        );
        passwordResetTokenRepository.saveAndFlush(PasswordResetToken.create(
                user, tokenHash, now.plusMinutes(30), now
        ));

        CountDownLatch bothRequestsAreHashing = new CountDownLatch(2);
        when(passwordEncoder.encode(any())).thenAnswer(invocation -> {
            bothRequestsAreHashing.countDown();
            if (!bothRequestsAreHashing.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("두 요청이 BCrypt 단계에서 동시에 대기하지 않았습니다.");
            }
            return "new-hash";
        });

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> reset(rawToken));
            Future<Boolean> second = executor.submit(() -> reset(rawToken));

            int successes = successfulRequests(first, second);

            assertThat(successes).isEqualTo(1);
        }

        User savedUser = userRepository.findById(user.getId()).orElseThrow();
        PasswordResetToken usedToken = passwordResetTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(savedUser.getPasswordHash()).isEqualTo("new-hash");
        assertThat(usedToken.isUsed()).isTrue();
    }

    private boolean reset(String rawToken) {
        passwordResetService.resetPassword(new PasswordResetRequest(
                rawToken, "NewPassword1!", "NewPassword1!"
        ));
        return true;
    }

    private int successfulRequests(Future<Boolean> first, Future<Boolean> second) throws Exception {
        int successes = 0;
        for (Future<Boolean> request : List.of(first, second)) {
            try {
                if (request.get(10, TimeUnit.SECONDS)) {
                    successes++;
                }
            } catch (ExecutionException exception) {
                if (!(exception.getCause() instanceof PasswordResetTokenExpiredException)) {
                    throw exception;
                }
            }
        }
        return successes;
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
