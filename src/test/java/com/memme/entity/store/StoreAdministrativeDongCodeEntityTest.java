package com.memme.entity.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.memme.entity.auth.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StoreAdministrativeDongCodeEntityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 10, 0);

    @Test
    void 매장에_10자리_행정동_코드를_저장하고_조회한다() {
        Store store = store();

        store.updateAdministrativeDongCode("1168051000");

        assertThat(store.getAdministrativeDongCode()).isEqualTo("1168051000");
    }

    @Test
    void 행정동_코드가_없는_매장을_허용한다() {
        Store store = store();

        assertThat(store.getAdministrativeDongCode()).isNull();
    }

    @Test
    void 여덟자리_행정동_코드는_거절한다() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> store().updateAdministrativeDongCode("11680510"));
    }

    @Test
    void 숫자가_아닌_행정동_코드는_거절한다() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> store().updateAdministrativeDongCode("11680510A0"));
    }

    private Store store() {
        User owner = User.create("store-code@example.com", "encoded-password", "01012345678", NOW);
        return Store.create(owner, null, null, "맴매카페", "06236", "서울특별시 강남구 테헤란로 123", null, NOW);
    }
}
