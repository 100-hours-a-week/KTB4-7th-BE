package com.memme.repository.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.entity.sales.SeoulDistrictQuarterBenchmarkEntity;
import com.memme.entity.sales.SeoulDistrictQuarterImportEntity;
import com.memme.entity.sales.SeoulDistrictQuarterImportStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SeoulDistrictQuarterRepositoryTest {

    @Autowired private SeoulDistrictQuarterImportRepository imports;
    @Autowired private SeoulDistrictQuarterBenchmarkRepository benchmarks;
    @Autowired private EntityManager entityManager;

    @Test
    void 완료된_분기를_최신순으로_찾고_행정동_요약을_조회한다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 16, 12, 0);
        SeoulDistrictQuarterImportEntity older = imports.saveAndFlush(
                SeoulDistrictQuarterImportEntity.start("2026Q1", "CS100010", now));
        older.complete(now.plusMinutes(1));
        SeoulDistrictQuarterImportEntity latest = imports.saveAndFlush(
                SeoulDistrictQuarterImportEntity.start("2026Q2", "CS100010", now));
        latest.complete(now.plusMinutes(1));
        SeoulDistrictQuarterImportEntity failed = imports.saveAndFlush(
                SeoulDistrictQuarterImportEntity.start("2026Q3", "CS100010", now));
        failed.fail(now.plusMinutes(1));
        imports.flush();
        benchmarks.saveAndFlush(SeoulDistrictQuarterBenchmarkEntity.create(
                latest, "1168051000", 7, 2_100_000L, "14-17", "17-21"));
        entityManager.clear();

        assertThat(imports.findByIndustryCodeAndStatusAndBasisQuarterLessThanEqualOrderByBasisQuarterDesc(
                "CS100010", SeoulDistrictQuarterImportStatus.COMPLETED, "2026Q3"))
                .extracting(SeoulDistrictQuarterImportEntity::getBasisQuarter)
                .containsExactly("2026Q2", "2026Q1");
        assertThat(benchmarks.findByImportedIdAndAdministrativeDongCode(latest.getId(), "1168051000"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getCafeStoreCount()).isEqualTo(7);
                    assertThat(saved.getDistrictAvgMonthlySalesPerCafe()).isEqualTo(2_100_000L);
                    assertThat(saved.getPeakSalesHourBand()).isEqualTo("14-17");
                });
        assertThat(benchmarks.findByImportedIdAndAdministrativeDongCode(older.getId(), "1168051000"))
                .isEmpty();
    }

    @Test
    void 같은_분기와_업종의_중복_수집을_거부한다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 16, 12, 0);
        imports.saveAndFlush(SeoulDistrictQuarterImportEntity.start("2026Q2", "CS100010", now));
        entityManager.clear();

        assertThatThrownBy(() -> imports.saveAndFlush(
                SeoulDistrictQuarterImportEntity.start("2026Q2", "CS100010", now)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void 같은_수집의_행정동_중복_요약을_거부한다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 16, 12, 0);
        SeoulDistrictQuarterImportEntity imported = imports.saveAndFlush(
                SeoulDistrictQuarterImportEntity.start("2026Q2", "CS100010", now));
        benchmarks.saveAndFlush(SeoulDistrictQuarterBenchmarkEntity.create(
                imported, "1168051000", 7, 2_100_000L, "14-17", "17-21"));
        entityManager.clear();

        assertThatThrownBy(() -> benchmarks.saveAndFlush(SeoulDistrictQuarterBenchmarkEntity.create(
                imported, "1168051000", 8, 2_200_000L, "17-21", "14-17")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
