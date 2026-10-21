package com.memme.repository.solution;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.memme.entity.solution.SolutionBundleEntity;
import com.memme.entity.solution.SolutionBundleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolutionBundleRepository extends JpaRepository<SolutionBundleEntity, Long> {

    Optional<SolutionBundleEntity> findByStoreIdAndTargetDate(Long storeId, LocalDate targetDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SolutionBundleEntity b "
            + "where b.storeId = :storeId and b.targetDate = :targetDate")
    Optional<SolutionBundleEntity> findByStoreIdAndTargetDateForUpdate(
            @Param("storeId") Long storeId,
            @Param("targetDate") LocalDate targetDate
    );

    Optional<SolutionBundleEntity> findByIdAndStoreId(Long id, Long storeId);

    List<SolutionBundleEntity> findAllByTargetDateAndStatus(LocalDate targetDate, SolutionBundleStatus status);

    Page<SolutionBundleEntity> findAllByTargetDateAndStatusOrderByIdAsc(
            LocalDate targetDate, SolutionBundleStatus status, Pageable pageable);
}
