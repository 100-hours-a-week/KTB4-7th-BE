package com.memme.repository.store;

import com.memme.entity.store.Store;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByOwnerId(Long ownerId);

    boolean existsByBusinessRegistrationNo(String businessRegistrationNo);
}
