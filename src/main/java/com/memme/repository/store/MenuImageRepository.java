package com.memme.repository.store;

import com.memme.entity.store.MenuImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuImageRepository extends JpaRepository<MenuImage, Long> {

    List<MenuImage> findAllByBatchIdAndStoreIdOrderByImageOrderAsc(Long batchId, Long storeId);
}
