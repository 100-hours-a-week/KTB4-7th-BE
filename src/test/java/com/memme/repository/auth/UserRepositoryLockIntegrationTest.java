package com.memme.repository.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserRepositoryLockIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void 비밀번호_해시_재확인_조회는_사용자행에_비관적_쓰기_잠금을_건다() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 23, 18, 0);
        User saved = userRepository.saveAndFlush(
                User.create("password-lock@example.com", "encoded-password", "01012345680", now)
        );
        entityManager.clear();

        User locked = userRepository.findByIdAndDeletedAtIsNullForUpdate(saved.getId()).orElseThrow();

        assertThat(entityManager.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
