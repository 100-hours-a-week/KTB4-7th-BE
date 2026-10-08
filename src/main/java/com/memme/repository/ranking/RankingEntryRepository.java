package com.memme.repository.ranking;

import java.util.List;
import java.util.Optional;

import com.memme.entity.ranking.RankingEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingEntryRepository extends JpaRepository<RankingEntryEntity, Long> {

    List<RankingEntryEntity> findAllByRankingSnapshotIdOrderByRankNoAsc(Long rankingSnapshotId);

    Optional<RankingEntryEntity> findByRankingSnapshotIdAndRankingProfileId(
            Long rankingSnapshotId,
            Long rankingProfileId
    );
}
