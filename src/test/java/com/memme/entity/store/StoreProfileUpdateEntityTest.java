package com.memme.entity.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.User;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class StoreProfileUpdateEntityTest {

    @Test
    void 전달된_매장_기본정보만_변경하고_변경시각을_갱신한다() {
        Store store = store();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 24, 22, 50);

        store.updateProfile(
                "수정된 맴매카페",
                null,
                "서울특별시 강남구 테헤란로 456",
                null,
                updatedAt
        );

        assertThat(store.getStoreName()).isEqualTo("수정된 맴매카페");
        assertThat(store.getPostalCode()).isEqualTo("06236");
        assertThat(store.getAddress()).isEqualTo("서울특별시 강남구 테헤란로 456");
        assertThat(store.getAddressDetail()).isEqualTo("101호");
        assertThat(store.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void 영업시간을_수정하면_휴무여부와_변경시각을_함께_갱신한다() {
        StoreBusinessHours businessHours = StoreBusinessHours.create(
                store(),
                1,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                false,
                LocalDateTime.of(2026, 9, 24, 9, 0)
        );
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 24, 22, 50);

        businessHours.updateBusinessHours(LocalTime.of(10, 0), LocalTime.of(20, 0), false, updatedAt);

        assertThat(businessHours.isClosed()).isFalse();
        assertThat(businessHours.getOpensAt()).isEqualTo(LocalTime.of(10, 0));
        assertThat(businessHours.getClosesAt()).isEqualTo(LocalTime.of(20, 0));
        assertThat(businessHours.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void 휴무일로_변경하면_영업시간을_비운다() {
        StoreBusinessHours businessHours = StoreBusinessHours.create(
                store(),
                7,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                false,
                LocalDateTime.of(2026, 9, 24, 9, 0)
        );

        businessHours.updateBusinessHours(null, null, true, LocalDateTime.of(2026, 9, 24, 22, 50));

        assertThat(businessHours.isClosed()).isTrue();
        assertThat(businessHours.getOpensAt()).isNull();
        assertThat(businessHours.getClosesAt()).isNull();
    }

    private Store store() {
        User owner = User.create(
                "owner@memme.com",
                "encoded-password",
                "01012345678",
                LocalDateTime.of(2026, 9, 24, 9, 0)
        );
        return Store.create(
                owner,
                "1234567890",
                LocalDateTime.of(2026, 9, 24, 9, 0),
                "맴매카페",
                "06236",
                "서울특별시 강남구 테헤란로 123",
                "101호",
                LocalDateTime.of(2026, 9, 24, 9, 0)
        );
    }
}
