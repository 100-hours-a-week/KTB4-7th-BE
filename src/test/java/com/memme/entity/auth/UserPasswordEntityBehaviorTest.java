package com.memme.entity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserPasswordEntityBehaviorTest {

    @Test
    void 사용자는_새_비밀번호_해시와_변경_시각으로_비밀번호를_변경한다() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 23, 18, 0);
        LocalDateTime changedAt = createdAt.plusMinutes(10);
        User user = User.create("owner@memme.com", "old-encoded-password", "01012345678", createdAt);

        user.changePassword("new-encoded-password", changedAt);

        assertThat(user.getPasswordHash()).isEqualTo("new-encoded-password");
        assertThat(readUpdatedAt(user)).isEqualTo(changedAt);
    }

    private LocalDateTime readUpdatedAt(User user) throws Exception {
        var field = User.class.getDeclaredField("updatedAt");
        field.setAccessible(true);
        return (LocalDateTime) field.get(user);
    }
}
