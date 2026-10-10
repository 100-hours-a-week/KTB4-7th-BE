package com.memme.repository.sales;

import java.time.LocalDateTime;
import java.util.List;

import com.memme.entity.sales.SalesOrderChannel;
import com.memme.entity.sales.SalesOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query(value = """
            select distinct sales_upload_id, year(ordered_at), month(ordered_at)
            from sales_orders
            where store_id = :storeId
            """, nativeQuery = true)
    List<Object[]> findDistinctUploadMonths(@Param("storeId") Long storeId);
}
