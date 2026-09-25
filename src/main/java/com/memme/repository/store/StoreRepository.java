package com.memme.repository.store;

import com.memme.entity.store.Store;
import com.memme.entity.store.StoreStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByOwnerId(Long ownerId);

    boolean existsByBusinessRegistrationNo(String businessRegistrationNo);

    List<Store> findAllByStatus(StoreStatus status);

    @Query("""
            select store.id as storeId, store.owner.id as ownerUserId
            from Store store
            where store.status = :status
            """)
    List<ActiveStoreOwner> findAllStoreOwnersByStatus(@Param("status") StoreStatus status);

    default List<ActiveStoreOwner> findAllActiveStoreOwners() {
        return findAllStoreOwnersByStatus(StoreStatus.ACTIVE);
    }

    interface ActiveStoreOwner {

        Long getStoreId();

        Long getOwnerUserId();
    }
}
