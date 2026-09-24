package com.memme.entity.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.User;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class StoreProfileEntityAccessTest {

    @Test
    void 매장_조회에_필요한_기본_정보를_반환한다() {
        Store store = store();

        assertThat(store.getBusinessRegistrationNo()).isEqualTo("1234567890");
        assertThat(store.getPostalCode()).isEqualTo("06236");
        assertThat(store.getAddress()).isEqualTo("서울특별시 강남구 테헤란로 123");
        assertThat(store.getAddressDetail()).isEqualTo("101호");
    }

    @Test
    void 매장_조회에_필요한_요일별_영업시간을_반환한다() {
        StoreBusinessHours businessHours = StoreBusinessHours.create(
                store(),
                1,
                LocalTime.of(9, 0),
                LocalTime.of(22, 0),
                false,
                LocalDateTime.of(2026, 9, 24, 19, 0)
        );

        assertThat(businessHours.getDayOfWeek()).isEqualTo(1);
        assertThat(businessHours.isClosed()).isFalse();
        assertThat(businessHours.getOpensAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(businessHours.getClosesAt()).isEqualTo(LocalTime.of(22, 0));
    }

    private Store store() {
        User owner = User.create(
                "owner@memme.com",
                "encoded-password",
                "01012345678",
                LocalDateTime.of(2026, 9, 24, 19, 0)
        );
        return Store.create(
                owner,
                "1234567890",
                LocalDateTime.of(2026, 9, 24, 19, 0),
                "맴매카페",
                "06236",
                "서울특별시 강남구 테헤란로 123",
                "101호",
                LocalDateTime.of(2026, 9, 24, 19, 0)
        );
    }
}
