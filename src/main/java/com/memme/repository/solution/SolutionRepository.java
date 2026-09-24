package com.memme.repository.solution;

import java.util.Collection;
import java.util.List;

import com.memme.entity.solution.SolutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionRepository extends JpaRepository<SolutionEntity, Long> {

    List<SolutionEntity> findAllBySolutionBundleIdOrderByRankNoAsc(Long solutionBundleId);

    List<SolutionEntity> findAllBySolutionBundleIdInOrderBySolutionBundleIdAscRankNoAsc(
            Collection<Long> solutionBundleIds
    );

    boolean existsBySolutionBundleIdAndRankNo(Long solutionBundleId, int rankNo);
}
