package com.memme.repository.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.ranking.RankingEntryEntity;
import com.memme.entity.ranking.RankingProfileEntity;
import com.memme.entity.ranking.RankingSnapshotEntity;
import com.memme.entity.ranking.RankingSnapshotStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RankingRepositoryIntegrationTest {

    @Autowired
    private RankingProfileRepository profileRepository;

    @Autowired
    private RankingSnapshotRepository snapshotRepository;

    @Autowired
    private RankingEntryRepository entryRepository;

    @Test
    void 매장_프로필과_기간별_최신_및_최신_완료_스냅샷을_조회한다() {
        LocalDate periodStart = LocalDate.of(2026, 10, 1);
        LocalDate periodEnd = LocalDate.of(2026, 10, 5);
        LocalDate comparisonStart = LocalDate.of(2026, 9, 1);
        LocalDate comparisonEnd = LocalDate.of(2026, 9, 5);
        RankingProfileEntity profile = profileRepository.saveAndFlush(
                RankingProfileEntity.create(31L, "사장님 184")
        );
        RankingSnapshotEntity completedSnapshot = RankingSnapshotEntity.create(
                periodStart, periodEnd, comparisonStart, comparisonEnd, 1
        );
        completedSnapshot.start();
        completedSnapshot.complete();
        snapshotRepository.saveAndFlush(completedSnapshot);

        RankingSnapshotEntity processingSnapshot = RankingSnapshotEntity.create(
                periodStart, periodEnd, comparisonStart, comparisonEnd, 2
        );
        processingSnapshot.start();
        snapshotRepository.saveAndFlush(processingSnapshot);

        assertThat(profileRepository.findByStoreId(31L))
                .get()
                .extracting(RankingProfileEntity::getAnonymousNickname)
                .isEqualTo("사장님 184");
        assertThat(snapshotRepository.findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndOrderByRevisionNoDesc(
                periodStart, periodEnd, comparisonStart, comparisonEnd
        ))
                .get()
                .extracting(RankingSnapshotEntity::getRevisionNo)
                .isEqualTo(2);
        assertThat(snapshotRepository.findTopByPeriodStartAndPeriodEndAndComparisonStartAndComparisonEndAndStatusOrderByRevisionNoDesc(
                periodStart,
                periodEnd,
                comparisonStart,
                comparisonEnd,
                RankingSnapshotStatus.COMPLETED
        ))
                .get()
                .extracting(RankingSnapshotEntity::getRevisionNo)
                .isEqualTo(1);
    }

    @Test
    void 스냅샷별_엔트리를_순위순으로_조회하고_내_엔트리를_조회한다() {
        RankingSnapshotEntity snapshot = RankingSnapshotEntity.create(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 5),
                1
        );
        snapshot.start();
        snapshot.complete();
        snapshotRepository.saveAndFlush(snapshot);

        RankingProfileEntity firstProfile = profileRepository.saveAndFlush(
                RankingProfileEntity.create(41L, "사장님 241")
        );
        RankingProfileEntity secondProfile = profileRepository.saveAndFlush(
                RankingProfileEntity.create(42L, "사장님 242")
        );
        entryRepository.saveAndFlush(RankingEntryEntity.create(
                snapshot.getId(), secondProfile.getId(), 3, new BigDecimal("12.0000"), 112L, 100L
        ));
        entryRepository.saveAndFlush(RankingEntryEntity.create(
                snapshot.getId(), firstProfile.getId(), 1, new BigDecimal("28.4500"), 128L, 100L
        ));

        assertThat(entryRepository.findAllByRankingSnapshotIdOrderByRankNoAsc(snapshot.getId()))
                .extracting(RankingEntryEntity::getRankNo)
                .containsExactly(1, 3);
        assertThat(entryRepository.findByRankingSnapshotIdAndRankingProfileId(snapshot.getId(), firstProfile.getId()))
                .get()
                .extracting(RankingEntryEntity::getGrowthRate)
                .isEqualTo(new BigDecimal("28.4500"));
    }
}
