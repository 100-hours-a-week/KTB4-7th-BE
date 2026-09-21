package com.memme.repository.sales;

import java.util.Collection;
import java.util.List;

import com.memme.entity.sales.SalesOrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItemEntity, Long> {

    List<SalesOrderItemEntity> findAllBySalesOrderIdOrderByIdAsc(Long salesOrderId);

    List<SalesOrderItemEntity> findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(
            Collection<Long> salesOrderIds
    );
}
