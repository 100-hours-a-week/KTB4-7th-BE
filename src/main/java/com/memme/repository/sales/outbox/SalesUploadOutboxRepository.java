package com.memme.repository.sales.outbox;

import com.memme.entity.sales.SalesUploadOutboxEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SalesUploadOutboxRepository extends JpaRepository<SalesUploadOutboxEntity, Long> {
    @Query(value = "SELECT id FROM stores WHERE id = :id FOR UPDATE", nativeQuery = true)
    Long lockStore(@Param("id") Long id);

    @Query(value = "SELECT id FROM stores WHERE id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<Long> tryLockStore(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from SalesUploadOutboxEntity j where j.id = :id")
    Optional<SalesUploadOutboxEntity> lockJob(@Param("id") Long id);

    @Query(value = """
            SELECT j.* FROM sales_upload_outbox j
            WHERE ((j.status = 'READY' AND j.available_at <= :now)
                OR (j.status = 'RUNNING' AND j.lease_until <= :now))
            AND NOT EXISTS (SELECT 1 FROM sales_upload_outbox previous
                WHERE previous.store_id = j.store_id AND previous.id < j.id
                AND previous.status IN ('READY', 'RUNNING'))
            ORDER BY j.available_at, j.id LIMIT 32
            """, nativeQuery = true)
    List<SalesUploadOutboxEntity> candidates(@Param("now") LocalDateTime now);

    @Query(value = "SELECT MIN(id) FROM sales_upload_outbox WHERE store_id = :storeId AND status IN ('READY','RUNNING')", nativeQuery = true)
    Long firstActiveId(@Param("storeId") Long storeId);
}
