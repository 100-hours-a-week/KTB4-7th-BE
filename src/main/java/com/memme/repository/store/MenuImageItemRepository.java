package com.memme.repository.store;

import com.memme.entity.store.MenuImageItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuImageItemRepository extends JpaRepository<MenuImageItem, Long> {

    List<MenuImageItem> findAllByMenuImageIdInOrderBySortOrderAscIdAsc(Collection<Long> menuImageIds);
}
