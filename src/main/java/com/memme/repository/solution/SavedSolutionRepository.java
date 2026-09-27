package com.memme.repository.solution;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.memme.entity.solution.SavedSolutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedSolutionRepository extends JpaRepository<SavedSolutionEntity, Long> {

    Optional<SavedSolutionEntity> findByIdAndUserId(Long id, Long userId);

    Optional<SavedSolutionEntity> findByUserIdAndSolutionId(Long userId, Long solutionId);

    List<SavedSolutionEntity> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    List<SavedSolutionEntity> findAllByUserIdAndSolutionIdIn(
            Long userId,
            Collection<Long> solutionIds
    );
}
