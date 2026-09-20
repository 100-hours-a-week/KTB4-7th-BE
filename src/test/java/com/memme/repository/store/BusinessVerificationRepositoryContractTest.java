package com.memme.repository.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.store.BusinessVerification;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

class BusinessVerificationRepositoryContractTest {

    @Test
    void 사업자_인증_결과를_저장하고_조회하는_Jpa_Repository를_제공한다() throws Exception {
        Class<?> repositoryClass = Class.forName("com.memme.repository.store.BusinessVerificationRepository");

        assertThat(JpaRepository.class.isAssignableFrom(repositoryClass)).isTrue();
        assertThat(repositoryClass.getGenericInterfaces())
                .anySatisfy(type -> assertThat(type.getTypeName()).contains(BusinessVerification.class.getName()));
    }
}
