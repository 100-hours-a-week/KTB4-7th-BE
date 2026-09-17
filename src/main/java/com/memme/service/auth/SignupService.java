package com.memme.service.auth;

import com.memme.dto.auth.SignupAccountRequest;
import com.memme.dto.auth.SignupAccountResponse;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^010\\d{8}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,20}$"
    );

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
        validate(request);

        if (userRepository.existsByEmail(request.email()) || userRepository.existsByPhone(request.phone())) {
            throw new DuplicateSignupException();
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

    private void validate(SignupAccountRequest request) {
        if (request == null
                || isInvalidEmail(request.email())
                || isInvalidPassword(request.password())
                || !request.password().equals(request.passwordConfirm())
                || isInvalidPhone(request.phone())
                || isInvalidAgreements(request.agreements())) {
            throw new InvalidSignupRequestException();
        }
    }

    private boolean isInvalidPassword(String password) {
        return password == null || !PASSWORD_PATTERN.matcher(password).matches();
    }

    private boolean isInvalidEmail(String email) {
        return email == null || !EMAIL_PATTERN.matcher(email).matches();
    }

    private boolean isInvalidPhone(String phone) {
        return phone == null || !PHONE_PATTERN.matcher(phone).matches();
    }

    private boolean isInvalidAgreements(SignupAccountRequest.Agreements agreements) {
        return agreements == null
                || !agreements.termsOfService()
                || isBlank(agreements.termsOfServiceVersion())
                || !agreements.privacyPolicy()
                || isBlank(agreements.privacyPolicyVersion());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
