package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordResetRateLimitKeyHasherTest {

    @Test
    void 이메일은_공백과_대소문자를_정규화한_뒤_비밀키로_HMAC_처리한다() {
        PasswordResetRateLimitKeyHasher hasher = new PasswordResetRateLimitKeyHasher("test-secret", "");

        String lowerCase = hasher.hashEmail("owner@memme.com");
        String mixedCase = hasher.hashEmail("  OWNER@MEMME.COM  ");

        assertThat(mixedCase).isEqualTo(lowerCase).hasSize(64).doesNotContain("owner");
        assertThat(hasher.hashIp("203.0.113.15")).isNotEqualTo(lowerCase);
    }

    @Test
    void 전용_비밀키가_없으면_DB_비밀번호를_키로_사용한다() {
        PasswordResetRateLimitKeyHasher hasher = new PasswordResetRateLimitKeyHasher("", "database-secret");

        assertThat(hasher.hashEmail("owner@memme.com"))
                .isEqualTo(new PasswordResetRateLimitKeyHasher("database-secret", "")
                        .hashEmail("owner@memme.com"));
    }
}
