package com.memme.repository.sales;

import java.time.LocalDateTime;
import java.util.List;

import com.memme.entity.sales.SalesOrderChannel;
import com.memme.entity.sales.SalesOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderRepository extends JpaRepository<SalesOrderEntity, Long> {

    boolean existsByStoreIdAndChannelAndPosOrderNoAndOrderedAt(
            Long storeId,
            SalesOrderChannel channel,
            String posOrderNo,
            LocalDateTime orderedAt
    );

    List<SalesOrderEntity> findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
            Long storeId,
            LocalDateTime periodStart,
            LocalDateTime periodEndExclusive
    );

    List<SalesOrderEntity> findAllBySalesUploadIdOrderByOrderedAtAsc(Long salesUploadId);
}
