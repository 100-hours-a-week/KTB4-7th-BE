package com.memme.entity.auth;
import static org.assertj.core.api.Assertions.assertThat;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
class UserEntityMappingTest { @Test void users_테이블을_매핑한다() { assertThat(User.class.isAnnotationPresent(Entity.class)).isTrue(); assertThat(User.class.getAnnotation(Table.class).name()).isEqualTo("users"); } }
