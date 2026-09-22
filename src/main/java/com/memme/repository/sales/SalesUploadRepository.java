package com.memme.repository.sales;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesUploadRepository extends JpaRepository<SalesUploadEntity, Long> {

    Page<SalesUploadEntity> findByStoreIdOrderByUploadedAtDesc(Long storeId, Pageable pageable);

    @Query("""
            select upload
            from SalesUploadEntity upload
            where upload.storeId = :storeId
              and upload.periodStart <= :periodEnd
              and upload.periodEnd >= :periodStart
            order by upload.uploadedAt desc
            """)
    Page<SalesUploadEntity> findOverlappingPeriod(
            @Param("storeId") Long storeId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            Pageable pageable
    );

    Optional<SalesUploadEntity> findFirstByStoreIdOrderByUploadedAtDesc(Long storeId);

    List<SalesUploadEntity> findAllByStoreIdAndFileChecksumOrderByUploadedAtDesc(
            Long storeId,
            String fileChecksum
    );

    @Query("""
            select coalesce(sum(upload.validRowCount), 0)
            from SalesUploadEntity upload
            where upload.storeId = :storeId
              and upload.status = :status
            """)
    long sumValidRowCount(
            @Param("storeId") Long storeId,
            @Param("status") SalesUploadStatus status
    );

    default long sumAppliedRecordCount(Long storeId, SalesUploadStatus status) {
        return sumValidRowCount(storeId, status);
    }
}
