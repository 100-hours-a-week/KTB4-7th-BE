package com.memme.repository.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.User;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

class UserRepositoryContractTest {

    @Test
    void 회원가입과_로그인에_필요한_Jpa_Repository를_제공한다() throws Exception {
        Class<?> repositoryClass = Class.forName("com.memme.repository.auth.UserRepository");

        assertThat(JpaRepository.class.isAssignableFrom(repositoryClass)).isTrue();
        assertThat(repositoryClass.getGenericInterfaces())
                .anySatisfy(type -> assertThat(type.getTypeName()).contains(User.class.getName()));

        Method existsByEmail = repositoryClass.getMethod("existsByEmail", String.class);
        Method existsByPhone = repositoryClass.getMethod("existsByPhone", String.class);
        Method findByEmail = repositoryClass.getMethod("findByEmail", String.class);

        assertThat(existsByEmail.getReturnType()).isEqualTo(boolean.class);
        assertThat(existsByPhone.getReturnType()).isEqualTo(boolean.class);
        assertThat(findByEmail.getReturnType()).isEqualTo(Optional.class);
    }
}
