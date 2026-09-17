package com.memme.repository.auth;
import com.memme.entity.auth.SignupDraft;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SignupDraftRepository extends JpaRepository<SignupDraft, Long> { Optional<SignupDraft> findBySignupTokenHash(String signupTokenHash); }
