package com.memme.repository.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Query(value = """
            select menu_total.menu_key as menuKey,
                   menu_total.standard_category as standardCategory,
                   menu_total.sales_amount as salesAmount,
                   menu_total.quantity as quantity,
                   menu_total.first_order_id as firstOrderId,
                   first_item.id as firstItemId,
                   first_item.menu_name as menuName
            from (
                select item.menu_key,
                       item.standard_category,
                       sum(item.net_amount) as sales_amount,
                       sum(item.quantity) as quantity,
                       min(item.sales_order_id) as first_order_id
                from sales_order_items item
                join sales_orders sales_order on sales_order.id = item.sales_order_id
                where sales_order.store_id = :storeId
                  and sales_order.ordered_at >= :startAt
                  and sales_order.ordered_at < :endAt
                  and item.item_type = 'MENU'
                group by item.menu_key, item.standard_category
            ) menu_total
            join sales_order_items first_item on first_item.id = (
                select min(candidate.id)
                from sales_order_items candidate
                where candidate.sales_order_id = menu_total.first_order_id
                  and candidate.menu_key = menu_total.menu_key
                  and candidate.standard_category = menu_total.standard_category
                  and candidate.item_type = 'MENU'
            )
            """, nativeQuery = true)
    List<MenuSalesAggregate> aggregateMenuSales(
            @Param("storeId") Long storeId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    @Query(value = """
            select cast(sales_order.ordered_at as date) as salesDate,
                   hour(sales_order.ordered_at) as salesHour,
                   sum(item.net_amount) as salesAmount
            from sales_order_items item
            join sales_orders sales_order on sales_order.id = item.sales_order_id
            where sales_order.store_id = :storeId
              and sales_order.ordered_at >= :startAt
              and sales_order.ordered_at < :endAt
            group by cast(sales_order.ordered_at as date), hour(sales_order.ordered_at)
            """, nativeQuery = true)
    List<HourlySalesAggregate> aggregateHourlySales(
            @Param("storeId") Long storeId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    interface MenuSalesAggregate {

        String getMenuKey();

        String getStandardCategory();

        Number getSalesAmount();

        Number getQuantity();

        Long getFirstOrderId();

        Long getFirstItemId();

        String getMenuName();
    }

    interface HourlySalesAggregate {

        LocalDate getSalesDate();

        Number getSalesHour();

        Number getSalesAmount();
    }
}
