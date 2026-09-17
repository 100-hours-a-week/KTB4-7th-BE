package com.memme.repository.auth;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
class SignupDraftRepositoryContractTest { @Test void 토큰_해시_조회_Repository를_제공한다() throws Exception { assertThat(JpaRepository.class.isAssignableFrom(SignupDraftRepository.class)).isTrue(); assertThat(SignupDraftRepository.class.getMethod("findBySignupTokenHash", String.class).getReturnType()).isEqualTo(Optional.class); } }
