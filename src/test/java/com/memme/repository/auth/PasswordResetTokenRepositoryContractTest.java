package com.memme.repository.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.PasswordResetToken;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

class PasswordResetTokenRepositoryContractTest {

    @Test
    void 토큰_해시로_비밀번호_재설정_토큰을_조회하는_Jpa_Repository를_제공한다() throws Exception {
        Class<?> repositoryClass = PasswordResetTokenRepository.class;

        assertThat(JpaRepository.class.isAssignableFrom(repositoryClass)).isTrue();
        assertThat(repositoryClass.getGenericInterfaces())
                .anySatisfy(type -> assertThat(type.getTypeName()).contains(PasswordResetToken.class.getName()));

        Method findByTokenHash = repositoryClass.getMethod("findByTokenHash", String.class);

        assertThat(findByTokenHash.getReturnType()).isEqualTo(Optional.class);
    }
}
