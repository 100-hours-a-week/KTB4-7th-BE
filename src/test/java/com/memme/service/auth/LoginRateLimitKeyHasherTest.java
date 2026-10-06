package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginRateLimitKeyHasherTest {

    @Test
    void 이메일은_앞뒤_공백과_대소문자를_정규화한_후_HMAC한다() {
        LoginRateLimitKeyHasher hasher = new LoginRateLimitKeyHasher("test-secret", "");

        assertThat(hasher.hashEmail(" Owner@Memme.com "))
                .isEqualTo(hasher.hashEmail("owner@memme.com"))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void 해시된_이메일과_IP는_원문과_서로_구분된다() {
        LoginRateLimitKeyHasher hasher = new LoginRateLimitKeyHasher("test-secret", "");

        assertThat(hasher.hashEmail("owner@memme.com")).isNotEqualTo("owner@memme.com");
        assertThat(hasher.hashIp("203.0.113.8")).isNotEqualTo("203.0.113.8");
        assertThat(hasher.hashEmail("owner@memme.com")).isNotEqualTo(hasher.hashIp("owner@memme.com"));
    }

    @Test
    void 별도_키가_비어있으면_DB_PASSWORD를_해시_키로_사용한다() {
        LoginRateLimitKeyHasher hasher = new LoginRateLimitKeyHasher("", "database-secret");

        assertThat(hasher.hashEmail("owner@memme.com"))
                .isEqualTo(new LoginRateLimitKeyHasher("database-secret", "").hashEmail("owner@memme.com"));
    }
}
