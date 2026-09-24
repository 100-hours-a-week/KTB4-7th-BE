package com.memme.repository.solution;

import java.time.LocalDate;
import java.util.Optional;

import com.memme.entity.solution.SolutionBundleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionBundleRepository extends JpaRepository<SolutionBundleEntity, Long> {

    Optional<SolutionBundleEntity> findByStoreIdAndTargetDate(Long storeId, LocalDate targetDate);

    Optional<SolutionBundleEntity> findByIdAndStoreId(Long id, Long storeId);
}
