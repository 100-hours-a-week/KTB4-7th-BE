package com.memme.entity.store;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BusinessVerificationEntityMappingTest {

    @Test
    void erd에_정의된_business_verifications_테이블을_매핑한다() throws Exception {
        Class<?> businessVerificationClass = Class.forName("com.memme.entity.store.BusinessVerification");

        assertThat(businessVerificationClass.isAnnotationPresent(Entity.class)).isTrue();

        Table table = businessVerificationClass.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo("business_verifications");
        assertThat(table.indexes())
                .extracting(Index::columnList)
                .contains("expires_at");

        assertColumn(businessVerificationClass, "businessRegNumber", "business_reg_number", 10, false);
        assertColumn(businessVerificationClass, "verifiedAt", "verified_at", 255, false);
        assertColumn(businessVerificationClass, "expiresAt", "expires_at", 255, false);
        assertColumn(businessVerificationClass, "usedAt", "used_at", 255, true);
        assertColumn(businessVerificationClass, "createdAt", "created_at", 255, false);
    }

    @Test
    void 사업자_인증_결과의_시각_필드는_LocalDateTime이다() throws Exception {
        Class<?> businessVerificationClass = Class.forName("com.memme.entity.store.BusinessVerification");

        assertThat(businessVerificationClass.getDeclaredField("verifiedAt").getType()).isEqualTo(LocalDateTime.class);
        assertThat(businessVerificationClass.getDeclaredField("expiresAt").getType()).isEqualTo(LocalDateTime.class);
        assertThat(businessVerificationClass.getDeclaredField("usedAt").getType()).isEqualTo(LocalDateTime.class);
        assertThat(businessVerificationClass.getDeclaredField("createdAt").getType()).isEqualTo(LocalDateTime.class);
    }

    private void assertColumn(
            Class<?> entityClass,
            String fieldName,
            String columnName,
            int length,
            boolean nullable
    ) throws NoSuchFieldException {
        Field field = entityClass.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.length()).isEqualTo(length);
        assertThat(column.nullable()).isEqualTo(nullable);
    }
}
