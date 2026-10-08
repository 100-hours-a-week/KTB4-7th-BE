package com.memme.entity.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RankingEntityMappingTest {

    @Test
    void 랭킹_프로필을_매장과_익명_닉네임에_매핑한다() throws Exception {
        Class<?> entityClass = RankingProfileEntity.class;

        assertThat(entityClass.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(entityClass.getAnnotation(Table.class).name()).isEqualTo("ranking_profiles");
        assertUniqueConstraint(entityClass, "store_id");
        assertUniqueConstraint(entityClass, "anonymous_nickname");
        assertColumn(entityClass, "storeId", "store_id", false, 255, 0, 0);
        assertColumn(entityClass, "anonymousNickname", "anonymous_nickname", false, 50, 0, 0);
        assertColumn(entityClass, "createdAt", "created_at", false, 255, 0, 0);
    }

    @Test
    void 스냅샷을_기간_revision_상태와_함께_매핑한다() throws Exception {
        Class<?> entityClass = RankingSnapshotEntity.class;

        assertThat(entityClass.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(entityClass.getAnnotation(Table.class).name()).isEqualTo("ranking_snapshots");
        assertUniqueConstraint(
                entityClass,
                "period_start", "period_end", "comparison_start", "comparison_end", "revision_no"
        );
        assertColumn(entityClass, "periodStart", "period_start", false, 255, 0, 0);
        assertColumn(entityClass, "periodEnd", "period_end", false, 255, 0, 0);
        assertColumn(entityClass, "comparisonStart", "comparison_start", false, 255, 0, 0);
        assertColumn(entityClass, "comparisonEnd", "comparison_end", false, 255, 0, 0);
        assertColumn(entityClass, "revisionNo", "revision_no", false, 255, 0, 0);
        assertColumn(entityClass, "calculatedAt", "calculated_at", true, 255, 0, 0);
        assertColumn(entityClass, "status", "status", false, 20, 0, 0);
        assertThat(entityClass.getDeclaredField("status").getAnnotation(Enumerated.class).value())
                .isEqualTo(EnumType.STRING);
    }

    @Test
    void 랭킹_엔트리를_순위_성장률과_기간별_매출에_매핑한다() throws Exception {
        Class<?> entityClass = RankingEntryEntity.class;

        assertThat(entityClass.isAnnotationPresent(Entity.class)).isTrue();
        assertThat(entityClass.getAnnotation(Table.class).name()).isEqualTo("ranking_entries");
        assertUniqueConstraint(entityClass, "ranking_snapshot_id", "ranking_profile_id");
        assertColumn(entityClass, "rankingSnapshotId", "ranking_snapshot_id", false, 255, 0, 0);
        assertColumn(entityClass, "rankingProfileId", "ranking_profile_id", false, 255, 0, 0);
        assertColumn(entityClass, "rankNo", "rank_no", false, 255, 0, 0);
        assertColumn(entityClass, "growthRate", "growth_rate", false, 255, 10, 4);
        assertColumn(entityClass, "selectedPeriodSales", "selected_period_sales", false, 255, 0, 0);
        assertColumn(entityClass, "comparisonPeriodSales", "comparison_period_sales", false, 255, 0, 0);
        assertThat(entityClass.getDeclaredField("growthRate").getType()).isEqualTo(BigDecimal.class);
    }

    @Test
    void 스냅샷_상태는_문자열로_저장한다() {
        assertThat(RankingSnapshotStatus.values())
                .containsExactly(
                        RankingSnapshotStatus.PENDING,
                        RankingSnapshotStatus.PROCESSING,
                        RankingSnapshotStatus.COMPLETED,
                        RankingSnapshotStatus.FAILED
                );
    }

    @Test
    void 스냅샷_기간과_엔트리_데이터를_생성한다() {
        RankingSnapshotEntity snapshot = RankingSnapshotEntity.create(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5),
                1
        );
        RankingEntryEntity entry = RankingEntryEntity.create(
                10L,
                20L,
                1,
                new BigDecimal("28.4500"),
                1_284_500L,
                1_000_000L
        );

        assertThat(snapshot.getStatus()).isEqualTo(RankingSnapshotStatus.PENDING);
        assertThat(snapshot.getRevisionNo()).isEqualTo(1);
        assertThat(entry.getRankingSnapshotId()).isEqualTo(10L);
        assertThat(entry.getRankingProfileId()).isEqualTo(20L);
        assertThat(entry.getGrowthRate()).isEqualByComparingTo("28.4500");
    }

    private void assertColumn(
            Class<?> entityClass,
            String fieldName,
            String columnName,
            boolean nullable,
            int length,
            int precision,
            int scale
    ) throws NoSuchFieldException {
        Field field = entityClass.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo(columnName);
        assertThat(column.nullable()).isEqualTo(nullable);
        assertThat(column.length()).isEqualTo(length);
        assertThat(column.precision()).isEqualTo(precision);
        assertThat(column.scale()).isEqualTo(scale);
    }

    private void assertUniqueConstraint(Class<?> entityClass, String... expectedColumns) {
        List<List<String>> constraints = Arrays.stream(entityClass.getAnnotation(Table.class).uniqueConstraints())
                .map(constraint -> List.of(constraint.columnNames()))
                .toList();

        assertThat(constraints).contains(List.of(expectedColumns));
    }
}
