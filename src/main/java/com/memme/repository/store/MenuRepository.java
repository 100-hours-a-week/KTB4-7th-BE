package com.memme.repository.store;

import com.memme.entity.store.Menu;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(Long storeId);

    Optional<Menu> findByIdAndStoreIdAndRemovedAtIsNull(Long id, Long storeId);
}
