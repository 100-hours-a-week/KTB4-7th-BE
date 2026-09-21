package com.memme.service.auth;

import com.memme.dto.auth.SignupBusinessRequest;
import com.memme.dto.auth.SignupBusinessResponse;
import com.memme.dto.common.FieldError;
import com.memme.entity.auth.SignupDraft;
import com.memme.entity.auth.User;
import com.memme.entity.noti.NotificationPreference;
import com.memme.entity.store.BusinessVerification;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.exception.BusinessStatusNotEligibleException;
import com.memme.exception.DuplicateSignupException;
import com.memme.exception.InvalidSignupRequestException;
import com.memme.exception.SignupCompletionExpiredException;
import com.memme.repository.auth.SignupDraftRepository;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.store.BusinessVerificationRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupBusinessService {

    private static final LocalTime NEXT_DAY_CLOSE_LIMIT = LocalTime.of(6, 0);

    private final SignupDraftRepository signupDraftRepository;
    private final BusinessVerificationRepository businessVerificationRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final StoreBusinessHoursRepository storeBusinessHoursRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final Clock clock;

    public SignupBusinessService(
            SignupDraftRepository signupDraftRepository,
            BusinessVerificationRepository businessVerificationRepository,
            UserRepository userRepository,
            StoreRepository storeRepository,
            StoreBusinessHoursRepository storeBusinessHoursRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            Clock clock
    ) {
        this.signupDraftRepository = signupDraftRepository;
        this.businessVerificationRepository = businessVerificationRepository;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.storeBusinessHoursRepository = storeBusinessHoursRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.clock = clock;
    }

    @Transactional
    public SignupBusinessResponse completeSignup(String signupToken, SignupBusinessRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        SignupDraft signupDraft = signupDraftRepository.findBySignupTokenHash(sha256(signupToken))
                .orElseThrow(() -> new SignupCompletionExpiredException(SignupCompletionExpiredException.Reason.SIGNUP_DRAFT));
        if (signupDraft.isExpiredAt(now)) {
            throw new SignupCompletionExpiredException(SignupCompletionExpiredException.Reason.SIGNUP_DRAFT);
        }

        BusinessVerification verification = businessVerificationRepository.findById(request.businessVerificationId())
                .orElseThrow(BusinessStatusNotEligibleException::new);
        if (verification.isExpiredAt(now)) {
            throw new SignupCompletionExpiredException(SignupCompletionExpiredException.Reason.BUSINESS_VERIFICATION);
        }
        if (verification.isUsed() || !verification.matchesBusinessRegNumber(request.businessRegNumber())) {
            throw new BusinessStatusNotEligibleException();
        }
        if (storeRepository.existsByBusinessRegistrationNo(request.businessRegNumber())) {
            throw new DuplicateSignupException(List.of(
                    new FieldError("businessRegNumber", "이미 등록된 사업자등록번호입니다.")
            ));
        }

        validateBusinessHours(request.businessHours());

        User user = userRepository.save(User.create(
                signupDraft.getEmail(), signupDraft.getPasswordHash(), signupDraft.getPhone(), now
        ));
        Store store = storeRepository.save(Store.create(
                user, request.businessRegNumber(), verification.getVerifiedAt(), request.storeName(), request.postalCode(),
                request.address(), request.addressDetail(), now
        ));
        List<StoreBusinessHours> businessHours = request.businessHours().stream()
                .map(hours -> StoreBusinessHours.create(
                        store,
                        hours.dayOfWeek().getValue(),
                        hours.isClosed() ? null : LocalTime.parse(hours.openTime()),
                        hours.isClosed() ? null : LocalTime.parse(hours.closeTime()),
                        hours.isClosed(),
                        now
                ))
                .toList();
        storeBusinessHoursRepository.saveAll(businessHours);
        notificationPreferenceRepository.save(NotificationPreference.create(user, now));
        verification.markUsedAt(now);

        return new SignupBusinessResponse(
                new SignupBusinessResponse.User(user.getId(), user.getEmail()),
                new SignupBusinessResponse.Store(store.getId(), store.getStoreName()),
                "LOGIN"
        );
    }

    private void validateBusinessHours(List<SignupBusinessRequest.BusinessHours> businessHours) {
        Set<Integer> dayValues = businessHours.stream()
                .map(hours -> hours.dayOfWeek().getValue())
                .collect(Collectors.toSet());
        if (businessHours.size() != 7 || dayValues.size() != 7) {
            throw invalidBusinessHours();
        }

        for (SignupBusinessRequest.BusinessHours hours : businessHours) {
            if (hours.isClosed()) {
                if (hours.openTime() != null || hours.closeTime() != null) {
                    throw invalidBusinessHours();
                }
                continue;
            }
            if (hours.openTime() == null || hours.closeTime() == null) {
                throw invalidBusinessHours();
            }
            LocalTime openTime = LocalTime.parse(hours.openTime());
            LocalTime closeTime = LocalTime.parse(hours.closeTime());
            if (openTime.getMinute() % 10 != 0 || closeTime.getMinute() % 10 != 0) {
                throw invalidBusinessHours();
            }
            if (closeTime.isBefore(openTime) && closeTime.isAfter(NEXT_DAY_CLOSE_LIMIT)) {
                throw invalidBusinessHours();
            }
        }
    }

    private InvalidSignupRequestException invalidBusinessHours() {
        return new InvalidSignupRequestException(List.of(
                new FieldError("businessHours", "영업시간을 확인해 주세요.")
        ));
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
