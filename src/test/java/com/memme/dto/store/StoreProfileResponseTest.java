package com.memme.dto.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoreProfileResponseTest {

    @Test
    void 내_매장_조회_응답_DTO는_매장_정보를_가진다() {
        assertTrue(StoreProfileResponse.class.isRecord());
        assertEquals(
                List.of("store"),
                componentNames(StoreProfileResponse.class)
        );

        assertEquals(
                List.of("id", "businessRegNumber", "storeName", "address", "businessHours"),
                componentNames(StoreProfileResponse.Store.class)
        );
    }

    @Test
    void 내_매장_조회_응답_DTO는_주소와_요일별_영업시간을_가진다() {
        assertEquals(
                List.of("postalCode", "roadAddress", "addressDetail"),
                componentNames(StoreProfileResponse.Address.class)
        );
        assertEquals(
                List.of("dayOfWeek", "isClosed", "openTime", "closeTime"),
                componentNames(StoreProfileResponse.BusinessHours.class)
        );

        RecordComponent[] businessHours = StoreProfileResponse.BusinessHours.class.getRecordComponents();
        assertEquals(DayOfWeek.class, businessHours[0].getType());
        assertEquals(Boolean.class, businessHours[1].getType());
        assertEquals(LocalTime.class, businessHours[2].getType());
        assertEquals(LocalTime.class, businessHours[3].getType());
    }

    private List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
