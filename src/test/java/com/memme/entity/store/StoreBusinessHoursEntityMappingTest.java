package com.memme.entity.store;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class StoreBusinessHoursEntityMappingTest {

    @Test
    void erd에_정의된_store_business_hours_테이블을_매핑한다() throws Exception {
        Class<?> businessHoursClass = Class.forName("com.memme.entity.store.StoreBusinessHours");

        assertThat(businessHoursClass.isAnnotationPresent(Entity.class)).isTrue();

        Table table = businessHoursClass.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo("store_business_hours");
        assertThat(table.uniqueConstraints())
                .extracting(UniqueConstraint::columnNames)
                .anySatisfy(columns -> assertThat(columns).containsExactly("store_id", "day_of_week"));

        assertStoreRelation(businessHoursClass);
        assertColumn(businessHoursClass, "dayOfWeek", "day_of_week", false);
        assertColumn(businessHoursClass, "opensAt", "opens_at", true);
        assertColumn(businessHoursClass, "closesAt", "closes_at", true);
        assertColumn(businessHoursClass, "closed", "is_closed", false);
    }

    @Test
    void 영업시간의_시간_필드를_erd_타입에_맞게_매핑한다() throws Exception {
        Class<?> businessHoursClass = Class.forName("com.memme.entity.store.StoreBusinessHours");

        assertThat(businessHoursClass.getDeclaredField("dayOfWeek").getType()).isEqualTo(Integer.class);
        assertThat(businessHoursClass.getDeclaredField("opensAt").getType()).isEqualTo(LocalTime.class);
        assertThat(businessHoursClass.getDeclaredField("closesAt").getType()).isEqualTo(LocalTime.class);
        assertThat(businessHoursClass.getDeclaredField("closed").getType()).isEqualTo(Boolean.class);
        assertThat(businessHoursClass.getDeclaredField("createdAt").getType()).isEqualTo(LocalDateTime.class);
        assertThat(businessHoursClass.getDeclaredField("updatedAt").getType()).isEqualTo(LocalDateTime.class);
    }

    private void assertStoreRelation(Class<?> businessHoursClass) throws NoSuchFieldException {
        Field field = businessHoursClass.getDeclaredField("store");
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(field.isAnnotationPresent(ManyToOne.class)).isTrue();
        assertThat(joinColumn).isNotNull();
        assertThat(joinColumn.name()).isEqualTo("store_id");
        assertThat(joinColumn.nullable()).isFalse();
    }

    private void assertColumn(
            Class<?> entityClass,
            String fieldName,
            String columnName,
            boolean nullable
    ) throws NoSuchFieldException {
        Field field = entityClass.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.nullable()).isEqualTo(nullable);
    }
}
