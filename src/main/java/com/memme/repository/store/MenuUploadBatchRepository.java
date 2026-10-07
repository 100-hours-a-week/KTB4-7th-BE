package com.memme.repository.store;

import com.memme.entity.store.MenuProcessingStatus;
import com.memme.entity.store.MenuUploadBatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuUploadBatchRepository extends JpaRepository<MenuUploadBatch, Long> {

    Optional<MenuUploadBatch> findByIdAndStoreId(Long id, Long storeId);

    Optional<MenuUploadBatch> findFirstByStoreIdOrderByUploadedAtDescIdDesc(Long storeId);

    Optional<MenuUploadBatch> findByStoreIdAndIdempotencyKeyHash(Long storeId, String idempotencyKeyHash);

    Optional<MenuUploadBatch> findFirstByStatusOrderByUploadedAtAscIdAsc(MenuProcessingStatus status);
}
