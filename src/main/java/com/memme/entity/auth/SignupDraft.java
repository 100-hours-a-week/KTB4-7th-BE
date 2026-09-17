package com.memme.entity.auth;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "signup_drafts")
public class SignupDraft {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false) private Long id;
    @Column(name = "signup_token_hash", nullable = false, unique = true, length = 64, columnDefinition = "CHAR(64)") private String signupTokenHash;
    @Column(name = "email", nullable = false, length = 100) private String email;
    @Column(name = "password_hash", nullable = false, length = 255) private String passwordHash;
    @Column(name = "phone", nullable = false, length = 20) private String phone;
    @Column(name = "terms_of_service_agreed", nullable = false) private boolean termsOfServiceAgreed;
    @Column(name = "terms_of_service_version", nullable = false, length = 50) private String termsOfServiceVersion;
    @Column(name = "privacy_policy_agreed", nullable = false) private boolean privacyPolicyAgreed;
    @Column(name = "privacy_policy_version", nullable = false, length = 50) private String privacyPolicyVersion;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    protected SignupDraft() {}
    public static SignupDraft create(String tokenHash, String email, String passwordHash, String phone, boolean tos, String tosVersion, boolean privacy, String privacyVersion, LocalDateTime expiresAt, LocalDateTime createdAt) {
        SignupDraft draft = new SignupDraft(); draft.signupTokenHash = tokenHash; draft.email = email; draft.passwordHash = passwordHash; draft.phone = phone; draft.termsOfServiceAgreed = tos; draft.termsOfServiceVersion = tosVersion; draft.privacyPolicyAgreed = privacy; draft.privacyPolicyVersion = privacyVersion; draft.expiresAt = expiresAt; draft.createdAt = createdAt; return draft;
    }
}
