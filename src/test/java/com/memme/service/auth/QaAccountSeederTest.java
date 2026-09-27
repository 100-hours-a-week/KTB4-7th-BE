package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.memme.config.QaAccountProperties;
import com.memme.entity.auth.User;
import com.memme.entity.noti.NotificationPreference;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.NotificationPreferenceRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class QaAccountSeederTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 20, 0);

    @Mock private UserRepository userRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private StoreBusinessHoursRepository storeBusinessHoursRepository;
    @Mock private NotificationPreferenceRepository notificationPreferenceRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private QaAccountSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = seeder(validProperties());
    }

    @Test
    void 유효한_설정이면_QA_사용자_매장_영업시간_알림설정을_생성한다() {
        when(userRepository.findByEmailAndDeletedAtIsNull("qa@memme.kr")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("qa@memme.kr")).thenReturn(false);
        when(userRepository.existsByPhone("01000000000")).thenReturn(false);
        when(storeRepository.existsByBusinessRegistrationNo("1234567891")).thenReturn(false);
        when(passwordEncoder.encode("QaPassword1!")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run(null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("qa@memme.kr");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(userCaptor.getValue().getPhone()).isEqualTo("01000000000");

        ArgumentCaptor<Store> storeCaptor = ArgumentCaptor.forClass(Store.class);
        verify(storeRepository).save(storeCaptor.capture());
        assertThat(storeCaptor.getValue().getBusinessRegistrationNo()).isEqualTo("1234567891");
        assertThat(storeCaptor.getValue().getStoreName()).isEqualTo("MEMME QA");
        assertThat(storeCaptor.getValue().getPostalCode()).isEqualTo("06236");

        verify(storeBusinessHoursRepository).saveAll(any());
        verify(storeBusinessHoursRepository).saveAll(org.mockito.ArgumentMatchers.argThat(hours -> {
            var savedHours = StreamSupport.stream(hours.spliterator(), false).toList();
            return savedHours.size() == 7
                    && savedHours.stream().map(StoreBusinessHours::getDayOfWeek).toList()
                    .equals(java.util.List.of(1, 2, 3, 4, 5, 6, 7))
                    && savedHours.stream().allMatch(hour -> !hour.isClosed())
                    && savedHours.stream().allMatch(hour -> LocalTime.of(9, 0).equals(hour.getOpensAt()))
                    && savedHours.stream().allMatch(hour -> LocalTime.of(18, 0).equals(hour.getClosesAt()));
        }));
        verify(notificationPreferenceRepository).save(any(NotificationPreference.class));
    }

    @Test
    void 이미_매장이_있는_QA_계정은_중복_생성하지_않는다() throws Exception {
        User existingUser = withId(User.create(
                "qa@memme.kr", "encoded-password", "01000000000", NOW
        ), 1L);
        Store existingStore = Store.create(
                existingUser, "1234567891", NOW, "MEMME QA", "06236", "서울특별시 강남구", null, NOW
        );
        when(userRepository.findByEmailAndDeletedAtIsNull("qa@memme.kr")).thenReturn(Optional.of(existingUser));
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(existingStore));

        seeder.run(null);

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
        verifyNoInteractions(storeBusinessHoursRepository, notificationPreferenceRepository);
    }

    @Test
    void QA_계정이_있지만_매장이_없으면_시작을_중단한다() throws Exception {
        User existingUser = withId(User.create(
                "qa@memme.kr", "encoded-password", "01000000000", NOW
        ), 1L);
        when(userRepository.findByEmailAndDeletedAtIsNull("qa@memme.kr")).thenReturn(Optional.of(existingUser));
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seeder.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("매장이 없습니다");

        verify(userRepository, never()).save(any());
    }

    @Test
    void 필수_환경변수가_올바르지_않으면_시작을_중단한다() {
        QaAccountProperties invalidProperties = new QaAccountProperties(
                true,
                "qa@memme.kr",
                "",
                "01000000000",
                "1234567891",
                "MEMME QA",
                "06236",
                "서울특별시 강남구",
                ""
        );

        assertThatThrownBy(() -> seeder(invalidProperties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QA_ACCOUNT_PASSWORD");

        verifyNoInteractions(userRepository, storeRepository, passwordEncoder);
    }

    @Test
    void 휴대폰번호나_사업자번호가_중복되면_시작을_중단한다() {
        when(userRepository.findByEmailAndDeletedAtIsNull("qa@memme.kr")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("qa@memme.kr")).thenReturn(false);
        when(userRepository.existsByPhone("01000000000")).thenReturn(true);

        assertThatThrownBy(() -> seeder.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QA_ACCOUNT_PHONE");

        verify(userRepository, never()).save(any());
        verify(storeRepository, never()).save(any());
    }

    private QaAccountSeeder seeder(QaAccountProperties properties) {
        Clock clock = Clock.fixed(Instant.parse("2026-09-27T11:00:00Z"), ZoneId.of("Asia/Seoul"));
        return new QaAccountSeeder(
                properties,
                userRepository,
                storeRepository,
                storeBusinessHoursRepository,
                notificationPreferenceRepository,
                passwordEncoder,
                clock
        );
    }

    private QaAccountProperties validProperties() {
        return new QaAccountProperties(
                true,
                " QA@MEMME.KR ",
                "QaPassword1!",
                "01000000000",
                "1234567891",
                "MEMME QA",
                "06236",
                "서울특별시 강남구",
                ""
        );
    }

    private <T> T withId(T target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
        return target;
    }
}
