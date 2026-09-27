package com.memme.service.auth;

import com.memme.config.QaAccountProperties;
import com.memme.entity.auth.User;
import com.memme.entity.noti.NotificationPreference;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.qa-account", name = "enabled", havingValue = "true")
public class QaAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QaAccountSeeder.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,20}$"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile("^010\\d{8}$");
    private static final Pattern BUSINESS_REGISTRATION_NO_PATTERN = Pattern.compile("^\\d{10}$");
    private static final Pattern POSTAL_CODE_PATTERN = Pattern.compile("^\\d{5}$");
    private static final Pattern STORE_NAME_PATTERN = Pattern.compile("^[가-힣A-Za-z0-9 ,&·-]+$");
    private static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_CLOSE_TIME = LocalTime.of(18, 0);

    private final QaAccountProperties properties;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final StoreBusinessHoursRepository storeBusinessHoursRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public QaAccountSeeder(
            QaAccountProperties properties,
            UserRepository userRepository,
            StoreRepository storeRepository,
            StoreBusinessHoursRepository storeBusinessHoursRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.storeBusinessHoursRepository = storeBusinessHoursRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validateProperties();

        String email = normalizedEmail();
        var existingUser = userRepository.findByEmailAndDeletedAtIsNull(email);
        if (existingUser.isPresent()) {
            if (storeRepository.findByOwnerId(existingUser.get().getId()).isEmpty()) {
                throw configurationException("QA_ACCOUNT_EMAIL에 해당하는 사용자에게 매장이 없습니다.");
            }
            log.info("QA 계정이 이미 존재하여 초기화를 건너뜁니다.");
            return;
        }

        validateUniqueness(email);

        LocalDateTime now = LocalDateTime.now(clock);
        User user = userRepository.save(User.create(
                email,
                passwordEncoder.encode(properties.password()),
                properties.phone(),
                now
        ));
        Store store = storeRepository.save(Store.create(
                user,
                properties.businessRegistrationNo(),
                now,
                properties.storeName(),
                properties.postalCode(),
                properties.address(),
                nullableText(properties.addressDetail()),
                now
        ));
        storeBusinessHoursRepository.saveAll(defaultBusinessHours(store, now));
        notificationPreferenceRepository.save(NotificationPreference.create(user, now));

        log.info("QA 계정 초기화를 완료했습니다.");
    }

    private void validateProperties() {
        validate(EMAIL_PATTERN, normalizedEmail(), "QA_ACCOUNT_EMAIL");
        validate(PASSWORD_PATTERN, properties.password(), "QA_ACCOUNT_PASSWORD");
        validate(PHONE_PATTERN, properties.phone(), "QA_ACCOUNT_PHONE");
        validate(
                BUSINESS_REGISTRATION_NO_PATTERN,
                properties.businessRegistrationNo(),
                "QA_ACCOUNT_BUSINESS_REGISTRATION_NO"
        );
        validate(POSTAL_CODE_PATTERN, properties.postalCode(), "QA_ACCOUNT_POSTAL_CODE");

        if (!hasText(properties.storeName())
                || properties.storeName().length() > 15
                || !STORE_NAME_PATTERN.matcher(properties.storeName()).matches()) {
            throw configurationException("QA_ACCOUNT_STORE_NAME 설정을 확인해 주세요.");
        }
        if (!hasText(properties.address()) || properties.address().length() > 255) {
            throw configurationException("QA_ACCOUNT_ADDRESS 설정을 확인해 주세요.");
        }
        if (properties.addressDetail() != null && properties.addressDetail().length() > 255) {
            throw configurationException("QA_ACCOUNT_ADDRESS_DETAIL 설정을 확인해 주세요.");
        }
    }

    private void validateUniqueness(String email) {
        if (userRepository.existsByEmail(email)) {
            throw configurationException("삭제된 계정을 포함해 QA_ACCOUNT_EMAIL이 이미 사용 중입니다.");
        }
        if (userRepository.existsByPhone(properties.phone())) {
            throw configurationException("QA_ACCOUNT_PHONE이 이미 사용 중입니다.");
        }
        if (storeRepository.existsByBusinessRegistrationNo(properties.businessRegistrationNo())) {
            throw configurationException("QA_ACCOUNT_BUSINESS_REGISTRATION_NO가 이미 사용 중입니다.");
        }
    }

    private List<StoreBusinessHours> defaultBusinessHours(Store store, LocalDateTime now) {
        return IntStream.rangeClosed(1, 7)
                .mapToObj(dayOfWeek -> StoreBusinessHours.create(
                        store,
                        dayOfWeek,
                        DEFAULT_OPEN_TIME,
                        DEFAULT_CLOSE_TIME,
                        false,
                        now
                ))
                .toList();
    }

    private void validate(Pattern pattern, String value, String environmentVariable) {
        if (!hasText(value) || !pattern.matcher(value).matches()) {
            throw configurationException(environmentVariable + " 설정을 확인해 주세요.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullableText(String value) {
        return hasText(value) ? value : null;
    }

    private IllegalStateException configurationException(String message) {
        return new IllegalStateException("QA 계정 초기화 설정 오류: " + message);
    }

    private String normalizedEmail() {
        return properties.email() == null ? null : properties.email().trim().toLowerCase(Locale.ROOT);
    }
}
