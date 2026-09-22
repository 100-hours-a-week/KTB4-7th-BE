package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.memme.entity.auth.User;
import com.memme.exception.AuthenticationRequiredException;
import com.memme.repository.auth.UserRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock private UserRepository userRepository;

    private WithdrawalService withdrawalService;

    @BeforeEach
    void setUp() {
        withdrawalService = new WithdrawalService(
                userRepository,
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
    }

    @Test
    void 사용자_탈퇴_시_deletedAt을_기록한다() throws Exception {
        User user = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        withdrawalService.withdraw(1L);

        assertThat(fieldValue(user, "deletedAt")).isEqualTo(LocalDateTime.of(2026, 9, 22, 21, 0));
    }

    @Test
    void 사용자가_없으면_인증_예외를_던진다() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(AuthenticationRequiredException.class, () -> withdrawalService.withdraw(1L));
    }

    private User user(Long id) throws Exception {
        User user = User.create(
                "owner@memme.com",
                "encoded-password",
                "01012345678",
                LocalDateTime.of(2026, 9, 22, 20, 0)
        );
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
        return user;
    }

    private Object fieldValue(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
