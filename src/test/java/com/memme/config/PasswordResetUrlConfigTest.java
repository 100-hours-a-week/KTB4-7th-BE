package com.memme.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PasswordResetUrlConfigTest {

    @Value("${password-reset.url}")
    private String passwordResetUrl;

    @Test
    void 비밀번호_재설정_링크_URL을_설정에서_읽는다() {
        assertThat(passwordResetUrl).isEqualTo("http://localhost:5173/password-reset");
    }
}
