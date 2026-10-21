package com.memme.repository.solution;

import java.util.Collection;
import java.util.List;

import com.memme.entity.solution.SolutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolutionRepository extends JpaRepository<SolutionEntity, Long> {

    @Query("select s from SolutionEntity s where s.solutionBundleId = :bundleId "
            + "and s.originalRankNo is null order by s.rankNo asc")
    List<SolutionEntity> findAllBySolutionBundleIdOrderByRankNoAsc(@Param("bundleId") Long solutionBundleId);

    @Query("select s from SolutionEntity s where s.solutionBundleId in :bundleIds "
            + "and s.originalRankNo is null order by s.solutionBundleId asc, s.rankNo asc")
    List<SolutionEntity> findAllBySolutionBundleIdInOrderBySolutionBundleIdAscRankNoAsc(
            @Param("bundleIds") Collection<Long> solutionBundleIds
    );

    @Query("select s from SolutionEntity s where s.solutionBundleId = :bundleId order by s.rankNo asc")
    List<SolutionEntity> findAllIncludingArchivedBySolutionBundleIdOrderByRankNoAsc(
            @Param("bundleId") Long solutionBundleId
    );

    @Query("select coalesce(max(s.rankNo), 0) from SolutionEntity s where s.solutionBundleId = :bundleId")
    int findMaxRankNoBySolutionBundleId(@Param("bundleId") Long solutionBundleId);

    boolean existsBySolutionBundleIdAndRankNo(Long solutionBundleId, int rankNo);
}
