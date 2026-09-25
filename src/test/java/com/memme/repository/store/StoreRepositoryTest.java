package com.memme.repository.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.store.Store;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

class StoreRepositoryTest {

    @Test
    void 매장_조회와_사업자등록번호_중복_확인을_위한_Repository를_제공한다() throws Exception {
        Class<?> repositoryClass = StoreRepository.class;

        assertThat(JpaRepository.class.isAssignableFrom(repositoryClass)).isTrue();

        Method findByOwnerId = repositoryClass.getMethod("findByOwnerId", Long.class);
        assertThat(findByOwnerId.getReturnType()).isEqualTo(Optional.class);

        Method existsByBusinessRegistrationNo = repositoryClass.getMethod(
                "existsByBusinessRegistrationNo",
                String.class
        );
        assertThat(existsByBusinessRegistrationNo.getReturnType()).isEqualTo(boolean.class);
    }

    @Test
    void 활성_매장의_매장_ID와_대표_사용자_ID를_조회한다() throws Exception {
        Method findAllActiveStoreOwners = StoreRepository.class.getMethod("findAllActiveStoreOwners");

        assertThat(findAllActiveStoreOwners.getReturnType()).isEqualTo(List.class);

        Method getStoreId = StoreRepository.ActiveStoreOwner.class.getMethod("getStoreId");
        Method getOwnerUserId = StoreRepository.ActiveStoreOwner.class.getMethod("getOwnerUserId");

        assertThat(getStoreId.getReturnType()).isEqualTo(Long.class);
        assertThat(getOwnerUserId.getReturnType()).isEqualTo(Long.class);
    }
}
