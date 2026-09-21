package com.memme.repository.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

class StoreBusinessHoursRepositoryTest {

    @Test
    void 매장별_요일순_영업시간_조회를_위한_Repository를_제공한다() throws Exception {
        Class<?> repositoryClass = Class.forName("com.memme.repository.store.StoreBusinessHoursRepository");

        assertThat(JpaRepository.class.isAssignableFrom(repositoryClass)).isTrue();

        Method method = repositoryClass.getMethod("findAllByStoreIdOrderByDayOfWeekAsc", Long.class);
        assertThat(method.getReturnType()).isEqualTo(List.class);
    }
}
