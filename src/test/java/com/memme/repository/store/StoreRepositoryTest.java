package com.memme.repository.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.store.Store;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

class StoreRepositoryTest {

    @Test
    void 매장_조회와_사업자등록번호_중복_확인을_위한_Repository를_제공한다() throws Exception {
        Class<?> repositoryClass = Class.forName("com.memme.repository.store.StoreRepository");

        assertThat(JpaRepository.class.isAssignableFrom(repositoryClass)).isTrue();

        Method findByOwnerId = repositoryClass.getMethod("findByOwnerId", Long.class);
        assertThat(findByOwnerId.getReturnType()).isEqualTo(Optional.class);

        Method existsByBusinessRegistrationNo = repositoryClass.getMethod(
                "existsByBusinessRegistrationNo",
                String.class
        );
        assertThat(existsByBusinessRegistrationNo.getReturnType()).isEqualTo(boolean.class);
    }
}
