package com.memme.repository.auth;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
class UserRepositoryContractTest { @Test void 중복_조회_Repository를_제공한다() throws Exception { assertThat(JpaRepository.class.isAssignableFrom(UserRepository.class)).isTrue(); assertThat(UserRepository.class.getMethod("existsByEmail", String.class).getReturnType()).isEqualTo(boolean.class); assertThat(UserRepository.class.getMethod("existsByPhone", String.class).getReturnType()).isEqualTo(boolean.class); } }
