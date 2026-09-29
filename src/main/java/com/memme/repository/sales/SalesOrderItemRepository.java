package com.memme.repository.sales;

import java.util.Collection;
import java.util.List;

import com.memme.entity.sales.SalesOrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItemEntity, Long> {

    @Query(value = """
            select count(*)
            from sales_order_items item
            join sales_orders sales_order on sales_order.id = item.sales_order_id
            where sales_order.store_id = :storeId
            """, nativeQuery = true)
    long countCurrentItemsByStoreId(@Param("storeId") Long storeId);

    List<SalesOrderItemEntity> findAllBySalesOrderIdOrderByIdAsc(Long salesOrderId);

    List<SalesOrderItemEntity> findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(
            Collection<Long> salesOrderIds
    );
}
