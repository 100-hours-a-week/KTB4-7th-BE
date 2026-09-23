package com.memme.repository.noti;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

class NotificationPreferenceRepositoryTest {

    @Test
    void 사용자별_알림_설정을_저장하는_Repository를_제공한다() throws Exception {
        Class<?> repositoryClass = NotificationPreferenceRepository.class;

        assertThat(JpaRepository.class.isAssignableFrom(repositoryClass)).isTrue();
    }
}
