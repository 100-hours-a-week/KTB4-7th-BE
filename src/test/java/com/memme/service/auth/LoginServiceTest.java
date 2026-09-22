package com.memme.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.LoginRequest;
import com.memme.entity.auth.User;
import com.memme.entity.store.Store;
import com.memme.exception.InvalidLoginException;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.store.StoreRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
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

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(userRepository, storeRepository, passwordEncoder);
    }

    @Test
    void 이메일과_비밀번호가_일치하면_사용자와_매장_정보를_반환한다() throws Exception {
        User user = user(1L);
        Store store = store(user, 10L);
        when(userRepository.findByEmailAndDeletedAtIsNull("owner@memme.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));

        LoginService.LoginResult result = loginService.login(new LoginRequest("owner@memme.com", "password"));

        assertEquals(1L, result.userId());
        assertEquals("owner@memme.com", result.email());
        assertEquals(10L, result.storeId());
    }

    @Test
    void 이메일에_해당하는_사용자가_없으면_로그인_실패_예외를_던진다() {
        when(userRepository.findByEmailAndDeletedAtIsNull("missing@memme.com")).thenReturn(Optional.empty());

        assertThrows(
                InvalidLoginException.class,
                () -> loginService.login(new LoginRequest("missing@memme.com", "password"))
        );
    }

    @Test
    void 비밀번호가_일치하지_않으면_로그인_실패_예외를_던진다() throws Exception {
        User user = user(1L);
        when(userRepository.findByEmailAndDeletedAtIsNull("owner@memme.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThrows(
                InvalidLoginException.class,
                () -> loginService.login(new LoginRequest("owner@memme.com", "wrong-password"))
        );
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
