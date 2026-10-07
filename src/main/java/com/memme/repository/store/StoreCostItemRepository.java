package com.memme.repository.store;

import com.memme.entity.store.StoreCostItem;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreCostItemRepository extends JpaRepository<StoreCostItem, Long> {
    Optional<StoreCostItem> findByStoreIdAndCostMonth(Long storeId, LocalDate costMonth);

    List<StoreCostItem> findAllByStoreIdAndCostMonthBetweenOrderByCostMonthAsc(
            Long storeId, LocalDate startMonth, LocalDate endMonth);

    Optional<StoreCostItem> findFirstByStoreIdAndCostMonthLessThanOrderByCostMonthDesc(
            Long storeId, LocalDate costMonth);
}
