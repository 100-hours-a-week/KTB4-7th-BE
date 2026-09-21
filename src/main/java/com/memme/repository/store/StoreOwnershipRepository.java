package com.memme.repository.store;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class StoreOwnershipRepository {

    private final EntityManager entityManager;

    public StoreOwnershipRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public boolean existsActiveStoreOwnedBy(Long storeId, Long userId) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*)
                from stores
                where id = :storeId
                  and owner_user_id = :userId
                  and status = 'ACTIVE'
                """)
                .setParameter("storeId", storeId)
                .setParameter("userId", userId)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
