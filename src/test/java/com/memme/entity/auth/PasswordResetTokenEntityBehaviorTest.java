package com.memme.entity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PasswordResetTokenEntityBehaviorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 18, 0);

    @Test
    void 재설정_토큰은_만료와_사용_상태를_확인하고_사용_완료_시각을_기록한다() {
        User user = User.create("owner@memme.com", "encoded-password", "01012345678", NOW);
        PasswordResetToken token = PasswordResetToken.create(
                user, "a".repeat(64), NOW.plusMinutes(30), NOW
        );

        assertThat(token.getUser()).isSameAs(user);
        assertThat(token.isExpiredAt(NOW.plusMinutes(29))).isFalse();
        assertThat(token.isExpiredAt(NOW.plusMinutes(30))).isTrue();
        assertThat(token.isUsed()).isFalse();

        token.markUsedAt(NOW.plusMinutes(1));

        assertThat(token.isUsed()).isTrue();
    }
}
