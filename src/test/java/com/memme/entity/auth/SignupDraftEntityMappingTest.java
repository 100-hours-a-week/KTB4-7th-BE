package com.memme.entity.auth;
import static org.assertj.core.api.Assertions.assertThat;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
class SignupDraftEntityMappingTest { @Test void signup_drafts_테이블을_매핑한다() { assertThat(SignupDraft.class.isAnnotationPresent(Entity.class)).isTrue(); assertThat(SignupDraft.class.getAnnotation(Table.class).name()).isEqualTo("signup_drafts"); } }
