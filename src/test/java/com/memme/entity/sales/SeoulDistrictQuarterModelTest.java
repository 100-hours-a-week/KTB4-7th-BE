package com.memme.entity.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SeoulDistrictQuarterModelTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 12, 0);

    @Test
    void 분기_수집_상태를_기록하고_완료한다() {
        SeoulDistrictQuarterImportEntity imported = SeoulDistrictQuarterImportEntity.start("2026Q2", "CS100010", NOW);

        assertThat(imported.getStatus()).isEqualTo(SeoulDistrictQuarterImportStatus.PROCESSING);
        imported.complete(NOW.plusMinutes(3));
        assertThat(imported.getStatus()).isEqualTo(SeoulDistrictQuarterImportStatus.COMPLETED);
        assertThat(imported.getCompletedAt()).isEqualTo(NOW.plusMinutes(3));
    }

    @Test
    void 실패한_수집은_완료_시각을_남기지_않는다() {
        SeoulDistrictQuarterImportEntity imported = SeoulDistrictQuarterImportEntity.start("2026Q2", "CS100010", NOW);
        imported.fail(NOW.plusMinutes(3));

        assertThat(imported.getStatus()).isEqualTo(SeoulDistrictQuarterImportStatus.FAILED);
        assertThat(imported.getCompletedAt()).isNull();
    }

    @Test
    void 요약에는_카페_점포가_세_곳_이상이어야_한다() {
        SeoulDistrictQuarterImportEntity imported = SeoulDistrictQuarterImportEntity.start("2026Q2", "CS100010", NOW);

        assertThatIllegalArgumentException().isThrownBy(() -> SeoulDistrictQuarterBenchmarkEntity.create(
                imported, "11680580", 2, 1_000_000L, "14-17", "17-21"));
    }

    @Test
    void 피크_시간대는_서울시_원본_구간만_허용한다() {
        SeoulDistrictQuarterImportEntity imported = SeoulDistrictQuarterImportEntity.start("2026Q2", "CS100010", NOW);

        assertThatIllegalArgumentException().isThrownBy(() -> SeoulDistrictQuarterBenchmarkEntity.create(
                imported, "11680580", 3, 1_000_000L, "15-18", "17-21"));
    }
}
