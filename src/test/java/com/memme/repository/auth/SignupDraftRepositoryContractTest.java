package com.memme.repository.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.SignupDraft;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

class SignupDraftRepositoryContractTest {

    @Test
    void 토큰_해시로_임시_가입_정보를_조회하는_Jpa_Repository를_제공한다() throws Exception {
        Class<?> repositoryClass = Class.forName("com.memme.repository.auth.SignupDraftRepository");

        assertThat(JpaRepository.class.isAssignableFrom(repositoryClass)).isTrue();
        assertThat(repositoryClass.getGenericInterfaces())
                .anySatisfy(type -> assertThat(type.getTypeName()).contains(SignupDraft.class.getName()));

        Method findBySignupTokenHash = repositoryClass.getMethod("findBySignupTokenHash", String.class);

        assertThat(findBySignupTokenHash.getReturnType()).isEqualTo(Optional.class);
    }
}
