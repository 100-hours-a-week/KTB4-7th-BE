package com.memme.repository.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.ranking.RankingSnapshotStatus;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RankingRepositoryContractTest {

    @Test
    void 매장으로_랭킹_프로필을_조회한다() throws Exception {
        Method method = RankingProfileRepository.class.getMethod("findByStoreId", Long.class);

        assertThat(method.getReturnType()).isEqualTo(Optional.class);
    }

    @Test
    void 기간별_최신_revision과_완료_스냅샷을_조회한다() throws Exception {
        Method latestRevision = RankingSnapshotRepository.class.getMethod(
                "findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndOrderByRevisionNoDesc",
                LocalDate.class, LocalDate.class, LocalDate.class, LocalDate.class
        );
        Method latestCompleted = RankingSnapshotRepository.class.getMethod(
                "findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndAndStatusOrderByRevisionNoDesc",
                LocalDate.class, LocalDate.class, LocalDate.class, LocalDate.class, RankingSnapshotStatus.class
        );

        assertThat(latestRevision.getReturnType()).isEqualTo(Optional.class);
        assertThat(latestCompleted.getReturnType()).isEqualTo(Optional.class);
    }

    @Test
    void 스냅샷별_순위순_엔트리와_특정_매장_엔트리를_조회한다() throws Exception {
        Method entries = RankingEntryRepository.class.getMethod(
                "findAllByRankingSnapshotIdOrderByRankNoAsc", Long.class
        );
        Method entry = RankingEntryRepository.class.getMethod(
                "findByRankingSnapshotIdAndRankingProfileId", Long.class, Long.class
        );

        assertThat(entries.getReturnType()).isEqualTo(List.class);
        assertThat(entry.getReturnType()).isEqualTo(Optional.class);
    }
}
