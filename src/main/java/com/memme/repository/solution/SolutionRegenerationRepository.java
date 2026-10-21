package com.memme.repository.solution;

import com.memme.entity.solution.SolutionRegenerationEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionRegenerationRepository extends JpaRepository<SolutionRegenerationEntity, Long> {

    List<SolutionRegenerationEntity> findAllByStoreIdAndTargetDateOrderByStartedAtDesc(
            Long storeId, LocalDate targetDate);

    Optional<SolutionRegenerationEntity> findByStoreIdAndTargetDateAndSourceFingerprint(
            Long storeId, LocalDate targetDate, String sourceFingerprint);
}
