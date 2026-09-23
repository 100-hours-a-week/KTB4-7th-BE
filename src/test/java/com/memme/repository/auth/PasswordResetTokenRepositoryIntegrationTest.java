package com.memme.repository.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.PasswordResetToken;
import com.memme.entity.auth.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PasswordResetTokenRepositoryIntegrationTest {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 사용자와_연결한_토큰_해시를_저장하고_조회한다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 23, 18, 0);
        User user = userRepository.saveAndFlush(
                User.create("reset-token@example.com", "encoded-password", "01012345678", now)
        );
        PasswordResetToken passwordResetToken = PasswordResetToken.create(
                user,
                "a".repeat(64),
                now.plusMinutes(30),
                now
        );

        passwordResetTokenRepository.saveAndFlush(passwordResetToken);
        entityManager.clear();

        assertThat(passwordResetTokenRepository.findByTokenHash("a".repeat(64))).isPresent();
    }
}
