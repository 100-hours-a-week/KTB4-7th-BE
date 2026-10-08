package com.memme.repository.store;

import com.memme.entity.store.MenuWriteOperation;
import com.memme.entity.store.MenuWriteRequest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuWriteRequestRepository extends JpaRepository<MenuWriteRequest, Long> {

    Optional<MenuWriteRequest> findByStoreIdAndOperationAndKeyHash(
            Long storeId, MenuWriteOperation operation, String keyHash);
}
