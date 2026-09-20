package com.memme.service.auth;

import com.memme.dto.auth.SignupAccountRequest;
import com.memme.dto.auth.SignupAccountResponse;
import com.memme.dto.common.FieldError;
import com.memme.entity.auth.SignupDraft;
import com.memme.exception.DuplicateSignupException;
import com.memme.exception.InvalidSignupRequestException;
import com.memme.repository.auth.SignupDraftRepository;
import com.memme.repository.auth.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {

    private final UserRepository userRepository;
    private final SignupDraftRepository signupDraftRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public SignupService(
            UserRepository userRepository,
            SignupDraftRepository signupDraftRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.signupDraftRepository = signupDraftRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public SignupAccountResponse signupAccount(SignupAccountRequest request) {
        validatePasswordConfirmation(request);

        boolean emailExists = userRepository.existsByEmail(request.email());
        boolean phoneExists = userRepository.existsByPhone(request.phone());
        if (emailExists || phoneExists) {
            throw new DuplicateSignupException(duplicateFieldErrors(emailExists, phoneExists));
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = now.plusHours(1);
        String signupToken = generateSignupToken();

        SignupAccountRequest.Agreements agreements = request.agreements();
        SignupDraft signupDraft = SignupDraft.create(
                sha256(signupToken),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.phone(),
                agreements.termsOfService(),
                agreements.termsOfServiceVersion(),
                agreements.privacyPolicy(),
                agreements.privacyPolicyVersion(),
                expiresAt.toLocalDateTime(),
                now.toLocalDateTime()
        );
        signupDraftRepository.save(signupDraft);

        return new SignupAccountResponse(signupToken, expiresAt);
    }

    private void validatePasswordConfirmation(SignupAccountRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new InvalidSignupRequestException(List.of(
                    new FieldError("passwordConfirm", "비밀번호와 일치하지 않습니다.")
            ));
        }
    }

    private List<FieldError> duplicateFieldErrors(boolean emailExists, boolean phoneExists) {
        List<FieldError> fieldErrors = new ArrayList<>();
        if (emailExists) {
            fieldErrors.add(new FieldError("email", "이미 사용 중인 이메일입니다."));
        }
        if (phoneExists) {
            fieldErrors.add(new FieldError("phone", "이미 사용 중인 휴대폰 번호입니다."));
        }
        return fieldErrors;
    }

    private String generateSignupToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return "signup_" + Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
