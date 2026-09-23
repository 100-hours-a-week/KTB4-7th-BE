package com.memme.entity.store;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StoreEntityMappingTest {

    @Test
    void erd에_정의된_stores_테이블을_매핑한다() throws Exception {
        Class<?> storeClass = Store.class;

        assertThat(storeClass.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(storeClass.getAnnotation(Table.class).name()).isEqualTo("stores");

        assertOwnerUser(storeClass);
        assertColumn(storeClass, "businessRegistrationNo", "business_registration_no", 10, false, true);
        assertColumn(storeClass, "name", "name", 100, false, false);
        assertColumn(storeClass, "postalCode", "postal_code", 5, false, false);
        assertColumn(storeClass, "address", "address", 255, false, false);
        assertColumn(storeClass, "addressDetail", "address_detail", 255, true, false);
        assertColumn(storeClass, "latitude", "latitude", 255, true, false);
        assertColumn(storeClass, "longitude", "longitude", 255, true, false);
        assertColumn(storeClass, "status", "status", 20, false, false);
    }

    @Test
    void 매장_위치와_시각_필드를_erd_타입에_맞게_매핑한다() throws Exception {
        Class<?> storeClass = Store.class;

        assertThat(storeClass.getDeclaredField("businessVerifiedAt").getType()).isEqualTo(LocalDateTime.class);
        assertThat(storeClass.getDeclaredField("latitude").getType()).isEqualTo(BigDecimal.class);
        assertThat(storeClass.getDeclaredField("longitude").getType()).isEqualTo(BigDecimal.class);
        assertThat(storeClass.getDeclaredField("createdAt").getType()).isEqualTo(LocalDateTime.class);
        assertThat(storeClass.getDeclaredField("updatedAt").getType()).isEqualTo(LocalDateTime.class);
    }

    private void assertOwnerUser(Class<?> storeClass) throws NoSuchFieldException {
        Field field = storeClass.getDeclaredField("owner");
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(field.isAnnotationPresent(OneToOne.class)).isTrue();
        assertThat(joinColumn).isNotNull();
        assertThat(joinColumn.name()).isEqualTo("owner_user_id");
        assertThat(joinColumn.nullable()).isFalse();
        assertThat(joinColumn.unique()).isTrue();
    }

    private void assertColumn(
            Class<?> entityClass,
            String fieldName,
            String columnName,
            int length,
            boolean nullable,
            boolean unique
    ) throws NoSuchFieldException {
        Field field = entityClass.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.length()).isEqualTo(length);
        assertThat(column.nullable()).isEqualTo(nullable);
        assertThat(column.unique()).isEqualTo(unique);
    }
}
