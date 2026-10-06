package com.memme.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.LoginRequest;
import com.memme.entity.auth.User;
import com.memme.entity.store.Store;
import com.memme.exception.auth.InvalidLoginException;
import com.memme.exception.auth.LoginRateLimitExceededException;
import com.memme.repository.auth.LoginRateLimitRepository;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.store.StoreRepository;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private LoginRateLimitRepository loginRateLimitRepository;
    @Mock private LoginRateLimitKeyHasher loginRateLimitKeyHasher;

    private LoginService loginService;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 12, 0);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        lenient().when(loginRateLimitKeyHasher.hashEmail(anyString())).thenReturn("email-hash");
        lenient().when(loginRateLimitKeyHasher.hashIp(anyString())).thenReturn("ip-hash");
        lenient().when(loginRateLimitRepository.checkAllowed(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        lenient().when(loginRateLimitRepository.recordFailureIfAllowed(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        lenient().when(loginRateLimitRepository.clearAccountFailuresIfAllowed(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        LoginRateLimiter rateLimiter = new LoginRateLimiter(
                loginRateLimitRepository, loginRateLimitKeyHasher, clock
        );
        loginService = new LoginService(userRepository, storeRepository, passwordEncoder, rateLimiter);
    }

    @Test
    void 이메일과_비밀번호가_일치하면_사용자와_매장_정보를_반환한다() throws Exception {
        User user = user(1L);
        Store store = store(user, 10L);
        when(userRepository.findByEmailAndDeletedAtIsNull("owner@memme.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));

        LoginService.LoginResult result = loginService.login(
                new LoginRequest("owner@memme.com", "password"), "203.0.113.8"
        );

        assertEquals(1L, result.userId());
        assertEquals("owner@memme.com", result.email());
        assertEquals(10L, result.storeId());
    }

    @Test
    void 이메일에_해당하는_사용자가_없으면_로그인_실패_예외를_던진다() {
        when(userRepository.findByEmailAndDeletedAtIsNull("missing@memme.com")).thenReturn(Optional.empty());

        assertThrows(
                InvalidLoginException.class,
                () -> loginService.login(new LoginRequest("missing@memme.com", "password"), "203.0.113.8")
        );
    }

    @Test
    void 비밀번호가_일치하지_않으면_로그인_실패_예외를_던진다() throws Exception {
        User user = user(1L);
        when(userRepository.findByEmailAndDeletedAtIsNull("owner@memme.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThrows(
                InvalidLoginException.class,
                () -> loginService.login(new LoginRequest("owner@memme.com", "wrong-password"), "203.0.113.8")
        );
    }

    @Test
    void 다섯번_실패한_뒤_계정_쿨다운에_걸리면_429용_예외를_던진다() {
        LoginRequest request = new LoginRequest("missing@memme.com", "wrong-password");
        AtomicInteger recordedFailures = new AtomicInteger();
        when(userRepository.findByEmailAndDeletedAtIsNull(request.email())).thenReturn(Optional.empty());
        when(loginRateLimitRepository.checkAllowed(anyString(), anyString(), any()))
                .thenAnswer(invocation -> recordedFailures.get() >= 5
                        ? Optional.of(NOW.plusSeconds(30))
                        : Optional.empty());
        when(loginRateLimitRepository.recordFailureIfAllowed(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    recordedFailures.incrementAndGet();
                    return Optional.empty();
                });

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(InvalidLoginException.class, () -> loginService.login(request, "203.0.113.8"));
        }

        LoginRateLimitExceededException exception = assertThrows(
                LoginRateLimitExceededException.class, () -> loginService.login(request, "203.0.113.8")
        );
        assertEquals(30, exception.getRetryAfterSeconds());
    }

    private User user(Long id) throws Exception {
        User user = User.create(
                "owner@memme.com",
                "encoded-password",
                "01012345678",
                LocalDateTime.of(2026, 9, 22, 20, 0)
        );
        return withId(user, id);
    }

    private Store store(User owner, Long id) throws Exception {
        Store store = Store.create(
                owner,
                "1234567890",
                LocalDateTime.of(2026, 9, 22, 20, 0),
                "맴매카페",
                "06236",
                "서울특별시 강남구 테헤란로 123",
                "101호",
                LocalDateTime.of(2026, 9, 22, 20, 0)
        );
        return withId(store, id);
    }

    private <T> T withId(T target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
        return target;
    }
}
