package com.memme.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MysqlInitScriptTest {

    @Test
    void 비밀번호_재설정_토큰_테이블을_초기화한다() throws IOException {
        String sql = Files.readString(Path.of("docker/mysql/init/01-auth-signup.sql"));

        assertThat(sql)
                .contains("CREATE TABLE password_reset_tokens")
                .contains("user_id BIGINT UNSIGNED NOT NULL")
                .contains("token_hash CHAR(64) NOT NULL")
                .contains("expires_at DATETIME NOT NULL")
                .contains("used_at DATETIME NULL")
                .contains("CONSTRAINT fk_password_reset_tokens_user");
    }

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
                .contains("business_registration_no CHAR(10) NULL")
                .contains("business_verified_at DATETIME NULL")
                .contains("postal_code CHAR(5) NOT NULL")
                .contains("latitude DECIMAL(10,7) NULL")
                .contains("longitude DECIMAL(10,7) NULL")
                .contains("CONSTRAINT fk_stores_owner_user");
    }

    @Test
    void 매장_요일별_영업시간_테이블을_초기화한다() throws IOException {
        String sql = Files.readString(Path.of("docker/mysql/init/01-auth-signup.sql"));

        assertThat(sql)
                .contains("CREATE TABLE store_business_hours")
                .contains("store_id BIGINT UNSIGNED NOT NULL")
                .contains("day_of_week INT NOT NULL")
                .contains("opens_at TIME NULL")
                .contains("closes_at TIME NULL")
                .contains("is_closed BOOLEAN NOT NULL DEFAULT FALSE")
                .contains("UNIQUE KEY uk_store_business_hours_store_day (store_id, day_of_week)");
    }

    @Test
    void 일별_매출_상태_컬럼을_UNKNOWN_기본값으로_초기화한다() throws IOException {
        String sql = Files.readString(Path.of("docker/mysql/init/02-sales-core.sql"));

        assertThat(sql)
                .contains("CREATE TABLE sales_daily_summaries")
                .contains("day_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'")
                .contains("UNIQUE KEY uk_sales_daily_summaries_store_date (store_id, sales_date)");
    }

    @Test
    void 사용자별_알림_설정_테이블을_false_기본값으로_초기화한다() throws IOException {
        String sql = Files.readString(Path.of("docker/mysql/init/01-auth-signup.sql"));

        assertThat(sql)
                .contains("CREATE TABLE notification_preferences")
                .contains("user_id BIGINT UNSIGNED NOT NULL")
                .contains("solution_enabled BOOLEAN NOT NULL DEFAULT FALSE")
                .contains("sales_upload_reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE")
                .contains("UNIQUE KEY uk_notification_preferences_user_id (user_id)")
                .contains("CONSTRAINT fk_notification_preferences_user");

        assertThat(sql).doesNotContain("ranking_change_enabled");
    }

    @Test
    void 알림함_테이블을_초기화한다() throws IOException {
        String sql = Files.readString(Path.of("docker/mysql/init/04-notifications.sql"));

        assertThat(sql)
                .contains("CREATE TABLE notifications")
                .contains("user_id BIGINT UNSIGNED NOT NULL")
                .contains("notification_type VARCHAR(50) NOT NULL")
                .contains("notification_key VARCHAR(100) NOT NULL")
                .contains("related_entity_type VARCHAR(50) NULL")
                .contains("related_entity_id BIGINT UNSIGNED NULL")
                .contains("sent_at DATETIME NULL")
                .contains("read_at DATETIME NULL")
                .contains("UNIQUE KEY uk_notifications_user_type_key (user_id, notification_type, notification_key)")
                .contains("CONSTRAINT fk_notifications_user");
    }

    @Test
    void 매장과_대상일자별_매출_예측_테이블을_초기화한다() throws IOException {
        String sql = Files.readString(Path.of("docker/mysql/init/02-sales-forecasts.sql"));

        assertThat(sql)
                .contains("CREATE TABLE sales_forecasts")
                .contains("based_on_upload_id BIGINT UNSIGNED NOT NULL")
                .contains("basis_date DATE NOT NULL")
                .contains("predicted_sales_amount BIGINT UNSIGNED NOT NULL")
                .contains("lower_bound BIGINT UNSIGNED NOT NULL")
                .contains("upper_bound BIGINT UNSIGNED NOT NULL")
                .contains("model_version VARCHAR(50) NOT NULL")
                .contains("generated_at DATETIME NOT NULL")
                .contains("UNIQUE KEY uk_sales_forecasts_store_target_date (store_id, target_date)")
                .doesNotContain("INSUFFICIENT_HISTORY");
    }

    @Test
    void 성장_랭킹_프로필_스냅샷_엔트리_테이블을_초기화한다() throws IOException {
        String sql = Files.readString(Path.of("docker/mysql/init/07-growth-ranking.sql"));

        assertThat(sql)
                .contains("CREATE TABLE ranking_profiles")
                .contains("store_id BIGINT UNSIGNED NOT NULL")
                .contains("anonymous_nickname VARCHAR(50) NOT NULL")
                .contains("UNIQUE KEY uk_ranking_profiles_store (store_id)")
                .contains("UNIQUE KEY uk_ranking_profiles_nickname (anonymous_nickname)")
                .contains("CREATE TABLE ranking_snapshots")
                .contains("revision_no INT UNSIGNED NOT NULL DEFAULT 1")
                .contains("CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))")
                .contains("UNIQUE KEY uk_ranking_snapshots_period_revision")
                .contains("CREATE TABLE ranking_entries")
                .contains("growth_rate DECIMAL(10,4) NOT NULL")
                .contains("UNIQUE KEY uk_ranking_entries_snapshot_profile")
                .contains("CONSTRAINT fk_ranking_entries_snapshot")
                .contains("CONSTRAINT fk_ranking_entries_profile");
    }

    @Test
    void 성장_랭킹_운영_마이그레이션은_기존_stores_id_타입에_맞춘다() throws IOException {
        String sql = Files.readString(Path.of("docker/mysql/migrations/20261007-add-growth-ranking.sql"));

        assertThat(sql)
                .contains("Existing QA/production stores.id is signed BIGINT")
                .contains("store_id BIGINT NOT NULL")
                .contains("CONSTRAINT fk_ranking_profiles_store FOREIGN KEY (store_id) REFERENCES stores(id)");
    }

    @Test
    void 솔루션과_사용자_저장_테이블을_초기화한다() throws IOException {
        String sql = Files.readString(Path.of("docker/mysql/init/03-solutions.sql"));

        assertThat(sql)
                .contains("CREATE TABLE solution_bundles")
                .contains("UNIQUE KEY uk_solution_bundle_store_date (store_id, target_date)")
                .contains("CREATE TABLE solutions")
                .contains("evidence_text TEXT NULL")
                .contains("UNIQUE KEY uk_solutions_bundle_rank (solution_bundle_id, rank_no)")
                .contains("CREATE TABLE saved_solutions")
                .contains("UNIQUE KEY uk_saved_solutions_user_solution (user_id, solution_id)");
    }

    @Test
    void 챗봇_메시지_테이블을_초기화한다() throws IOException {
        String sql = Files.readString(Path.of("docker/mysql/init/03-solutions.sql"));

        assertThat(sql)
                .contains("CREATE TABLE chat_messages")
                .contains("solution_bundle_id BIGINT UNSIGNED NULL")
                .contains("evidence_json JSON NULL")
                .contains("CHECK (role IN ('USER','ASSISTANT'))")
                .contains("CHECK (status IN ('PENDING','STREAMING','COMPLETED','FAILED'))");
    }
}
