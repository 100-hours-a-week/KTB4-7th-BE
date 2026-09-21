package com.memme.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MysqlInitScriptTest {

    @Test
    void 사업자_인증_결과_테이블을_초기화한다() throws IOException {
        String sql = Files.readString(Path.of("docker/mysql/init/01-auth-signup.sql"));

        assertThat(sql)
                .contains("CREATE TABLE business_verifications")
                .contains("business_reg_number CHAR(10) NOT NULL")
                .contains("expires_at DATETIME NOT NULL")
                .contains("KEY idx_business_verifications_expires_at (expires_at)");
    }

    @Test
    void 매장_테이블을_초기화한다() throws IOException {
        String sql = Files.readString(Path.of("docker/mysql/init/01-auth-signup.sql"));

        assertThat(sql)
                .contains("CREATE TABLE stores")
                .contains("owner_user_id BIGINT UNSIGNED NOT NULL")
                .contains("business_registration_no CHAR(10) NOT NULL")
                .contains("postal_code CHAR(5) NOT NULL")
                .contains("latitude DECIMAL(10,7) NULL")
                .contains("longitude DECIMAL(10,7) NULL")
                .contains("CONSTRAINT fk_stores_owner_user");
    }
}
