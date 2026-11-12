package com.memme.repository.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.entity.sales.SalesOrderChannel;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemStatus;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.store.MenuCategory;
import com.memme.repository.sales.SalesOrderBulkInsertRepository.OrderNaturalKey;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SalesOrderBulkInsertRepositoryTest {

    private static final String ORDER_COLUMNS =
            "sales_upload_id, store_id, channel, pos_order_no, ordered_at, is_valid";
    private static final String ITEM_COLUMNS = """
            menu_id, order_status, menu_name_raw, menu_name, menu_key, category_raw, standard_category,
            option_raw, quantity, line_price, unit_price, option_price, item_discount_name,
            item_discount_amount, order_discount_name, order_discount_amount, net_amount, taxable,
            vat_amount, item_type
            """;

    @Autowired private SalesOrderBulkInsertRepository bulkInsertRepository;
    @Autowired private SalesOrderRepository orderRepository;
    @Autowired private SalesOrderItemRepository itemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void 주문과_항목을_JPA_저장과_같은_값으로_묶어서_저장한다() {
        LocalDateTime orderedAt = LocalDateTime.of(2026, 9, 1, 9, 30, 15);
        SalesOrderEntity jpaOrder = orderRepository.save(order(901L, 9001L, SalesOrderChannel.POS, "A-1", orderedAt));
        itemRepository.save(item(jpaOrder.getId(), "아메리카노", SalesOrderItemStatus.COMPLETED, 2));
        entityManager.flush();

        assertThat(bulkInsertRepository.insertOrders(List.of(
                order(902L, 9002L, SalesOrderChannel.POS, "A-1", orderedAt)
        ))).isEqualTo(1);
        Long bulkOrderId = bulkInsertRepository.findOrderIdsBySalesUploadId(902L).values().iterator().next();
        assertThat(bulkInsertRepository.insertItems(List.of(
                item(bulkOrderId, "아메리카노", SalesOrderItemStatus.COMPLETED, 2)
        ))).isEqualTo(1);

        Map<String, Object> jpaOrderRow = orderRow(jpaOrder.getId());
        Map<String, Object> bulkOrderRow = orderRow(bulkOrderId);
        jpaOrderRow.remove("SALES_UPLOAD_ID");
        jpaOrderRow.remove("STORE_ID");
        bulkOrderRow.remove("SALES_UPLOAD_ID");
        bulkOrderRow.remove("STORE_ID");
        assertThat(bulkOrderRow).isEqualTo(jpaOrderRow);
        assertThat(itemRow(bulkOrderId)).isEqualTo(itemRow(jpaOrder.getId()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sales_order_items WHERE sales_order_id = ? AND created_at IS NOT NULL",
                Integer.class, bulkOrderId
        )).isEqualTo(1);
    }

    @Test
    void 비어_있는_선택값은_NULL로_저장한다() {
        bulkInsertRepository.insertOrders(List.of(
                order(903L, 9003L, SalesOrderChannel.KIOSK, "K-1", LocalDateTime.of(2026, 9, 2, 12, 0))
        ));
        Long orderId = bulkInsertRepository.findOrderIdsBySalesUploadId(903L).values().iterator().next();

        bulkInsertRepository.insertItems(List.of(SalesOrderItemEntity.create(new SalesOrderItemEntity.Values(
                orderId, null, SalesOrderItemStatus.COMPLETED, "라떼", "라떼", "라떼", " ",
                MenuCategory.COFFEE, null, 1, 4_500, 4_500, 0, null, 0, "", 0, 4_500, true, 409,
                SalesOrderItemType.MENU
        ))));

        assertThat(jdbcTemplate.queryForMap("""
                SELECT menu_id, category_raw, option_raw, item_discount_name, order_discount_name
                FROM sales_order_items WHERE sales_order_id = ?
                """, orderId)).allSatisfy((column, value) -> assertThat(value).isNull());
    }

    @Test
    void 오백건을_넘으면_여러_묶음으로_나눠_저장한다() {
        long uploadId = 904L;
        List<SalesOrderEntity> orders = IntStream.rangeClosed(1, 1_201)
                .mapToObj(number -> order(uploadId, 9004L, SalesOrderChannel.POS, "B-" + number,
                        LocalDateTime.of(2026, 9, 3, 0, 0).plusMinutes(number)))
                .toList();

        assertThat(SalesOrderBulkInsertRepository.insertStatementCount(orders.size())).isEqualTo(3);
        assertThat(bulkInsertRepository.insertOrders(orders)).isEqualTo(1_201);

        Map<OrderNaturalKey, Long> orderIds = bulkInsertRepository.findOrderIdsBySalesUploadId(uploadId);
        List<SalesOrderItemEntity> items = orderIds.values().stream()
                .map(orderId -> item(orderId, "아메리카노", SalesOrderItemStatus.COMPLETED, 1))
                .toList();
        assertThat(bulkInsertRepository.insertItems(items)).isEqualTo(1_201);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sales_order_items i
                JOIN sales_orders o ON o.id = i.sales_order_id
                WHERE o.sales_upload_id = ?
                """, Integer.class, uploadId)).isEqualTo(1_201);
    }

    @Test
    void 업로드의_주문_ID를_자연키로_정확히_매핑한다() {
        long uploadId = 905L;
        long storeId = 9005L;
        LocalDateTime orderedAt = LocalDateTime.of(2026, 9, 4, 18, 0, 1);
        bulkInsertRepository.insertOrders(List.of(
                order(uploadId, storeId, SalesOrderChannel.POS, "C-1", orderedAt),
                order(uploadId, storeId, SalesOrderChannel.KIOSK, "C-1", orderedAt),
                order(uploadId, storeId, SalesOrderChannel.POS, "C-1", orderedAt.plusSeconds(1)),
                order(906L, storeId, SalesOrderChannel.POS, "C-2", orderedAt)
        ));

        Map<OrderNaturalKey, Long> orderIds = bulkInsertRepository.findOrderIdsBySalesUploadId(uploadId);

        assertThat(orderIds).hasSize(3);
        orderIds.forEach((key, id) -> assertThat(jdbcTemplate.queryForMap(
                "SELECT store_id, channel, pos_order_no, ordered_at FROM sales_orders WHERE id = ?", id
        )).containsEntry("STORE_ID", key.storeId())
                .containsEntry("CHANNEL", key.channel().name())
                .containsEntry("POS_ORDER_NO", key.posOrderNo()));
        assertThat(orderIds).containsKey(
                new OrderNaturalKey(storeId, SalesOrderChannel.KIOSK, "C-1", orderedAt));
        assertThat(bulkInsertRepository.findOrderIdsBySalesUploadId(907L)).isEmpty();
    }

    @Test
    void 빈_목록은_저장하지_않는다() {
        assertThat(SalesOrderBulkInsertRepository.insertStatementCount(0)).isZero();
        assertThat(bulkInsertRepository.insertOrders(List.of())).isZero();
        assertThat(bulkInsertRepository.insertItems(List.of())).isZero();
    }

    @Test
    void 업로드_ID가_없으면_조회하지_않는다() {
        assertThatThrownBy(() -> bulkInsertRepository.findOrderIdsBySalesUploadId(null))
                .isInstanceOf(NullPointerException.class);
    }

    private Map<String, Object> orderRow(Long orderId) {
        return jdbcTemplate.queryForMap("SELECT " + ORDER_COLUMNS + " FROM sales_orders WHERE id = ?", orderId);
    }

    private Map<String, Object> itemRow(Long orderId) {
        return jdbcTemplate.queryForMap(
                "SELECT " + ITEM_COLUMNS + " FROM sales_order_items WHERE sales_order_id = ?", orderId);
    }

    private static SalesOrderEntity order(
            Long uploadId, Long storeId, SalesOrderChannel channel, String posOrderNo, LocalDateTime orderedAt
    ) {
        return SalesOrderEntity.create(uploadId, storeId, channel, posOrderNo, orderedAt, true);
    }

    private static SalesOrderItemEntity item(
            Long orderId, String menuName, SalesOrderItemStatus status, int quantity
    ) {
        long unitPrice = 2_000;
        long linePrice = quantity * unitPrice;
        long optionPrice = 500L * quantity;
        long itemDiscount = -100;
        long orderDiscount = -200;
        long netAmount = linePrice + optionPrice + itemDiscount + orderDiscount;
        return SalesOrderItemEntity.create(new SalesOrderItemEntity.Values(
                orderId, 31L, status, menuName + " (ICE)", menuName, menuName, "커피",
                MenuCategory.COFFEE, "샷 추가", quantity, linePrice, unitPrice, optionPrice,
                "쿠폰", itemDiscount, "회원 할인", orderDiscount, netAmount, true, netAmount / 11,
                SalesOrderItemType.MENU
        ));
    }
}
