package com.memme.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.memme.dto.auth.UserProfileResponse;
import com.memme.entity.auth.User;
import com.memme.entity.store.Store;
import com.memme.exception.AuthenticationRequiredException;
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

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private StoreRepository storeRepository;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userRepository, storeRepository);
    }

    @Test
    void 로그인한_사용자의_계정정보와_대표_매장명을_반환한다() throws Exception {
        User user = user(1L);
        Store store = store(user, 10L);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));

        UserProfileResponse response = userProfileService.getProfile(1L);

        assertThat(response.user().id()).isEqualTo(1L);
        assertThat(response.user().email()).isEqualTo("owner@memme.com");
        assertThat(response.user().phone()).isEqualTo("01012345678");
        assertThat(response.user().storeName()).isEqualTo("맴매카페");
    }

    @Test
    void 탈퇴했거나_없는_사용자이면_인증_예외를_던진다() {
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThrows(AuthenticationRequiredException.class, () -> userProfileService.getProfile(1L));
    }

    @Test
    void 대표_매장이_없으면_인증_예외를_던진다() throws Exception {
        User user = user(1L);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.empty());

        assertThrows(AuthenticationRequiredException.class, () -> userProfileService.getProfile(1L));
    }

    private User user(Long id) throws Exception {
        User user = User.create(
                "owner@memme.com",
                "encoded-password",
                "01012345678",
                LocalDateTime.of(2026, 9, 24, 9, 0)
        );
        return withId(user, id);
    }

    private Store store(User owner, Long id) throws Exception {
        Store store = Store.create(
                owner,
                "1234567890",
                LocalDateTime.of(2026, 9, 24, 9, 0),
                "맴매카페",
                "06236",
                "서울특별시 강남구 테헤란로 123",
                "101호",
                LocalDateTime.of(2026, 9, 24, 9, 0)
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
