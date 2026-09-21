package com.memme.entity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.store.BusinessVerification;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreBusinessHours;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class SignupStepTwoEntityBehaviorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 10, 0);

    @Test
    void 임시_가입_정보는_회원가입_완료에_필요한_값과_만료_여부를_제공한다() {
        SignupDraft draft = SignupDraft.create(
                "token-hash", "owner@memme.com", "encoded-password", "01012345678",
                true, "v1", true, "v1", NOW.plusHours(1), NOW
        );

        assertThat(draft.getEmail()).isEqualTo("owner@memme.com");
        assertThat(draft.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(draft.getPhone()).isEqualTo("01012345678");
        assertThat(draft.isExpiredAt(NOW)).isFalse();
        assertThat(draft.isExpiredAt(NOW.plusHours(1))).isTrue();
    }

    @Test
    void 사업자_인증_결과는_번호를_확인하고_사용_완료_상태로_변경한다() throws Exception {
        BusinessVerification verification = BusinessVerification.create(
                "1234567890", NOW, NOW.plusMinutes(10), NOW
        );

        assertThat(verification.matchesBusinessRegNumber("1234567890")).isTrue();
        assertThat(verification.isExpiredAt(NOW.plusMinutes(10))).isTrue();
        assertThat(verification.isUsed()).isFalse();

        verification.markUsedAt(NOW.plusMinutes(1));

        assertThat(verification.isUsed()).isTrue();
        assertThat(readField(verification, "usedAt")).isEqualTo(NOW.plusMinutes(1));
    }

    @Test
    void 회원가입_완료에_필요한_사용자_매장_영업시간을_생성한다() throws Exception {
        User user = User.create("owner@memme.com", "encoded-password", "01012345678", NOW);
        Store store = Store.create(
                user, "1234567890", NOW, "맴매카페", "06236", "서울특별시 강남구 테헤란로 123", "101호", NOW
        );
        StoreBusinessHours businessHours = StoreBusinessHours.create(
                store, 1, LocalTime.of(9, 0), LocalTime.of(18, 0), false, NOW
        );

        assertThat(user.getEmail()).isEqualTo("owner@memme.com");
        assertThat(store.getStoreName()).isEqualTo("맴매카페");
        assertThat(readField(businessHours, "dayOfWeek")).isEqualTo(1);
        assertThat(readField(businessHours, "opensAt")).isEqualTo(LocalTime.of(9, 0));
        assertThat(readField(businessHours, "closed")).isEqualTo(false);
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
