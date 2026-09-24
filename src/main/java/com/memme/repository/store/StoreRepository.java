package com.memme.repository.store;

import com.memme.entity.store.Store;
import com.memme.entity.store.StoreStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByOwnerId(Long ownerId);

    boolean existsByBusinessRegistrationNo(String businessRegistrationNo);

    List<Store> findAllByStatus(StoreStatus status);
}
