package com.memme.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.memme.dto.store.StoreProfileResponse;
import com.memme.entity.auth.User;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.exception.store.StoreProfileNotFoundException;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class StoreProfileServiceTest {

    @Mock private StoreRepository storeRepository;
    @Mock private StoreBusinessHoursRepository storeBusinessHoursRepository;

    private StoreProfileService storeProfileService;

    @BeforeEach
    void setUp() {
        storeProfileService = new StoreProfileService(storeRepository, storeBusinessHoursRepository);
    }

    @Test
    void 로그인한_사용자의_매장_정보와_주간_영업시간을_반환한다() throws Exception {
        Store store = store(10L);
        StoreBusinessHours monday = StoreBusinessHours.create(
                store, 1, LocalTime.of(9, 0), LocalTime.of(18, 0), false, LocalDateTime.now()
        );
        StoreBusinessHours sunday = StoreBusinessHours.create(
                store, 7, LocalTime.of(9, 0), LocalTime.of(18, 0), true, LocalDateTime.now()
        );
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.of(store));
        when(storeBusinessHoursRepository.findAllByStoreIdOrderByDayOfWeekAsc(10L))
                .thenReturn(List.of(monday, sunday));

        StoreProfileResponse response = storeProfileService.getProfile(1L);

        assertThat(response.store().id()).isEqualTo(10L);
        assertThat(response.store().businessRegNumber()).isEqualTo("123-45-*****");
        assertThat(response.store().storeName()).isEqualTo("맴매카페");
        assertThat(response.store().address().postalCode()).isEqualTo("06236");
        assertThat(response.store().address().roadAddress()).isEqualTo("서울특별시 강남구 테헤란로 123");
        assertThat(response.store().businessHours()).extracting(StoreProfileResponse.BusinessHours::dayOfWeek)
                .containsExactly(DayOfWeek.MONDAY, DayOfWeek.SUNDAY);
        assertThat(response.store().businessHours().get(0).openTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.store().businessHours().get(1).isClosed()).isTrue();
        assertThat(response.store().businessHours().get(1).openTime()).isNull();
        assertThat(response.store().businessHours().get(1).closeTime()).isNull();
    }

    @Test
    void 사용자_소유_매장이_없으면_매장_조회_예외를_던진다() {
        when(storeRepository.findByOwnerId(1L)).thenReturn(Optional.empty());

        assertThrows(StoreProfileNotFoundException.class, () -> storeProfileService.getProfile(1L));
    }

    private Store store(Long id) throws Exception {
        User owner = User.create(
                "owner@memme.com", "encoded-password", "01012345678", LocalDateTime.of(2026, 9, 24, 9, 0)
        );
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
