package com.memme.repository.store;

import com.memme.entity.store.StoreBusinessHours;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreBusinessHoursRepository extends JpaRepository<StoreBusinessHours, Long> {

    List<StoreBusinessHours> findAllByStoreIdOrderByDayOfWeekAsc(Long storeId);
}
