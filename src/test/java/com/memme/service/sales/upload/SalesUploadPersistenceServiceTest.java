package com.memme.service.sales.upload;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.entity.sales.SalesDailyStatus;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.service.sales.DailySalesSummary;
import com.memme.service.sales.SalesChannel;
import com.memme.service.sales.SalesItemType;
import com.memme.service.sales.SalesOrder;
import com.memme.service.sales.SalesOrderItem;
import com.memme.service.sales.SalesOrderKey;
import com.memme.service.sales.SalesOrderStatus;
import com.memme.entity.store.MenuCategory;
import com.memme.service.sales.TossPosWorkbookData;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.repository.sales.SalesOrderBulkInsertRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SalesUploadPersistenceServiceTest {

    private final SalesUploadPersistenceService persistenceService;
    private final SalesUploadRepository uploadRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final SalesDailySummaryRepository dailySummaryRepository;

    @Autowired private StoreBusinessHoursRepository businessHoursRepository;
    @Autowired private SalesDailyStatusResolver dailyStatusResolver;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired
    SalesUploadPersistenceServiceTest(
            SalesUploadPersistenceService persistenceService,
            SalesUploadRepository uploadRepository,
            SalesOrderRepository orderRepository,
            SalesOrderItemRepository itemRepository,
            SalesDailySummaryRepository dailySummaryRepository
    ) {
        this.persistenceService = persistenceService;
        this.uploadRepository = uploadRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.dailySummaryRepository = dailySummaryRepository;
    }

    @Test
    void reuploadReplacesOrdersWithinFileCoverage() {
        long storeId = 801L;
        TossPosWorkbookData workbookData = workbookData();

        Long firstUploadId = createValidatingUpload(storeId, "first");
        SalesUploadResult first = persistenceService.replaceCoverage(firstUploadId, storeId, workbookData);

        assertThat(first.appliedRecordCount()).isEqualTo(3);
        assertThat(orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(firstUploadId)).hasSize(3);
        assertThat(itemRepository.findAll()).hasSizeGreaterThanOrEqualTo(3);

        Long secondUploadId = createValidatingUpload(storeId, "second");
        SalesUploadResult second = persistenceService.replaceCoverage(secondUploadId, storeId, workbookData);

        assertThat(second.appliedRecordCount()).isEqualTo(3);
        assertThat(orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(firstUploadId)).isEmpty();
        assertThat(orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(secondUploadId)).hasSize(3);

        var summary = dailySummaryRepository.findByStoreIdAndSalesDate(
                storeId,
                LocalDate.of(2026, 9, 1)
        ).orElseThrow();
        assertThat(summary.getTotalNetAmount()).isEqualTo(1_000);
        assertThat(summary.getMenuNetAmount()).isEqualTo(1_000);
        assertThat(summary.getOrderCount()).isEqualTo(2);
        assertThat(summary.getSalesActivityCount()).isEqualTo(3);
        assertThat(summary.getMenuQuantity()).isEqualTo(2);
        assertThat(summary.getDayStatus()).isEqualTo(SalesDailyStatus.COMPLETE);
    }

    @Test
    void dailySummariesMatchAggregationOfStoredRowsAcrossDaysAndItemTypes() {
        long storeId = 802L;
        LocalDate day1 = LocalDate.of(2026, 9, 1);
        LocalDate day2 = LocalDate.of(2026, 9, 2);
        LocalDate outside = LocalDate.of(2026, 9, 3);

        Long outsideUploadId = createValidatingUpload(storeId, "outside");
        SalesOrderItem outsideItem = item(outside, SalesOrderStatus.COMPLETED, SalesChannel.POS, "900001",
                outside.atTime(12, 0), "라떼", 1, 4_000, 4_000, 4_000, SalesItemType.MENU);
        persistenceService.replaceCoverage(outsideUploadId, storeId, new TossPosWorkbookData(
                outside, outside, List.of(new SalesOrder(outsideItem.orderKey(), true, List.of(outsideItem))),
                List.of(outsideItem), List.of()
        ));
        dailySummaryRepository.saveAndFlush(SalesDailySummaryEntity.create(
                storeId, day2, 99_999, 99_999, 99, 99, 99, SalesDailyStatus.COMPLETE));

        SalesOrderItem menu = item(day1, SalesOrderStatus.COMPLETED, SalesChannel.POS, "100001",
                day1.atTime(9, 0, 15), "아메리카노", 2, 4_000, 2_000, 4_000, SalesItemType.MENU);
        SalesOrderItem delivery = item(day1, SalesOrderStatus.COMPLETED, SalesChannel.POS, "100001",
                day1.atTime(9, 0, 15), "배달비", 1, 3_000, 3_000, 3_000, SalesItemType.DELIVERY_FEE);
        SalesOrderItem canceled = item(day2, SalesOrderStatus.CANCELED, SalesChannel.KIOSK, "100002",
                day2.atTime(13, 30), "라떼", -1, -4_500, 4_500, -4_500, SalesItemType.MENU);
        SalesOrderItem goods = item(day2, SalesOrderStatus.COMPLETED, SalesChannel.KIOSK, "100003",
                day2.atTime(20, 59, 59), "원두", 1, 15_000, 15_000, 15_000, SalesItemType.GOODS);
        List<SalesOrderItem> items = List.of(menu, delivery, canceled, goods);
        TossPosWorkbookData data = new TossPosWorkbookData(day1, day2, List.of(
                new SalesOrder(menu.orderKey(), true, List.of(menu, delivery)),
                new SalesOrder(canceled.orderKey(), false, List.of(canceled)),
                new SalesOrder(goods.orderKey(), true, List.of(goods))
        ), items, List.of());

        Long uploadId = createValidatingUpload(storeId, "multi-day");
        SalesUploadResult result = persistenceService.replaceCoverage(uploadId, storeId, data);

        assertThat(result.appliedRecordCount()).isEqualTo(4);
        assertThat(orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(outsideUploadId)).hasSize(1);
        assertThat(summaryValues(storeId, day1)).containsExactly(7_000L, 4_000L, 1L, 2L, 1L);
        assertThat(summaryValues(storeId, day2)).containsExactly(10_500L, -4_500L, 1L, -1L, 2L);
        for (LocalDate date : List.of(day1, day2)) {
            assertThat(summaryValues(storeId, date)).isEqualTo(aggregateFromStoredRows(storeId, date));
        }
    }

    @Test
    void retriesLockConflictAndAppliesUploadOnce() {
        long storeId = 803L;
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger pauses = new AtomicInteger();
        SalesUploadPersistenceService service = serviceWith(failingBulkInsert(attempts, 1), pauses);
        TossPosWorkbookData workbookData = workbookData();

        Long uploadId = createValidatingUpload(storeId, "retry");
        SalesUploadResult result = service.replaceCoverage(uploadId, storeId, workbookData);

        assertThat(attempts).hasValue(2);
        assertThat(pauses).hasValue(1);
        assertThat(result.appliedRecordCount()).isEqualTo(3);
        assertThat(orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(uploadId)).hasSize(3);
        assertThat(summaryValues(storeId, LocalDate.of(2026, 9, 1))).containsExactly(1_000L, 1_000L, 2L, 2L, 3L);
    }

    @Test
    void rethrowsLockConflictAfterRetriesAndKeepsPreviousData() {
        long storeId = 804L;
        TossPosWorkbookData workbookData = workbookData();
        Long previousUploadId = createValidatingUpload(storeId, "previous");
        persistenceService.replaceCoverage(previousUploadId, storeId, workbookData);

        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger pauses = new AtomicInteger();
        SalesUploadPersistenceService service = serviceWith(failingBulkInsert(attempts, Integer.MAX_VALUE), pauses);
        Long uploadId = createValidatingUpload(storeId, "exhausted");

        assertThatThrownBy(() -> service.replaceCoverage(uploadId, storeId, workbookData))
                .isInstanceOf(CannotAcquireLockException.class);
        assertThat(attempts).hasValue(SalesUploadPersistenceService.MAX_LOCK_RETRIES + 1);
        assertThat(pauses).hasValue(SalesUploadPersistenceService.MAX_LOCK_RETRIES);
        assertThat(orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(previousUploadId)).hasSize(3);
        assertThat(orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(uploadId)).isEmpty();
    }

    @Test
    void doesNotRetryInsideCallerTransaction() {
        long storeId = 805L;
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger pauses = new AtomicInteger();
        SalesUploadPersistenceService service = serviceWith(failingBulkInsert(attempts, 1), pauses);
        Long uploadId = createValidatingUpload(storeId, "outer");

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                service.replaceCoverage(uploadId, storeId, workbookData())
        )).isInstanceOf(CannotAcquireLockException.class);
        assertThat(attempts).hasValue(1);
        assertThat(pauses).hasValue(0);
    }

    private SalesUploadPersistenceService serviceWith(SalesOrderBulkInsertRepository bulkInsert, AtomicInteger pauses) {
        return new SalesUploadPersistenceService(
                uploadRepository, orderRepository, itemRepository, dailySummaryRepository,
                businessHoursRepository, dailyStatusResolver, bulkInsert, transactionManager,
                new LockRetryBackoff() {
                    @Override
                    public void pause(int attempt) {
                        pauses.incrementAndGet();
                    }
                }
        );
    }

    /** 기존 주문을 지운 뒤 주문 저장 단계에서 failures번까지 락 충돌을 낸다. */
    private SalesOrderBulkInsertRepository failingBulkInsert(AtomicInteger attempts, int failures) {
        return new SalesOrderBulkInsertRepository(jdbcTemplate) {
            @Override
            public int insertOrders(List<SalesOrderEntity> orders) {
                if (attempts.incrementAndGet() <= failures) {
                    throw new CannotAcquireLockException("Lock wait timeout exceeded");
                }
                return super.insertOrders(orders);
            }
        };
    }

    private List<Long> summaryValues(long storeId, LocalDate date) {
        SalesDailySummaryEntity summary = dailySummaryRepository.findByStoreIdAndSalesDate(storeId, date).orElseThrow();
        return List.of(summary.getTotalNetAmount(), summary.getMenuNetAmount(), (long) summary.getOrderCount(),
                (long) summary.getMenuQuantity(), (long) summary.getSalesActivityCount());
    }

    /** 전환 전 방식처럼 저장된 주문·항목을 DB에서 다시 읽어 집계한다. */
    private List<Long> aggregateFromStoredRows(long storeId, LocalDate date) {
        Map<String, Object> items = jdbcTemplate.queryForMap("""
                SELECT COALESCE(SUM(i.net_amount), 0) AS total_net,
                       COALESCE(SUM(CASE WHEN i.item_type = 'MENU' THEN i.net_amount ELSE 0 END), 0) AS menu_net,
                       COALESCE(SUM(CASE WHEN i.item_type = 'MENU' THEN i.quantity ELSE 0 END), 0) AS menu_qty
                FROM sales_order_items i JOIN sales_orders o ON o.id = i.sales_order_id
                WHERE o.store_id = ? AND o.ordered_at >= ? AND o.ordered_at < ?
                """, storeId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        Map<String, Object> orders = jdbcTemplate.queryForMap("""
                SELECT COUNT(*) AS activity, COALESCE(SUM(CASE WHEN is_valid THEN 1 ELSE 0 END), 0) AS valid_orders
                FROM sales_orders WHERE store_id = ? AND ordered_at >= ? AND ordered_at < ?
                """, storeId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        List<Long> values = new ArrayList<>();
        values.add(((Number) items.get("TOTAL_NET")).longValue());
        values.add(((Number) items.get("MENU_NET")).longValue());
        values.add(((Number) orders.get("VALID_ORDERS")).longValue());
        values.add(((Number) items.get("MENU_QTY")).longValue());
        values.add(((Number) orders.get("ACTIVITY")).longValue());
        return values;
    }

    @Test
    void batchesAcrossBoundariesKeepEveryDetailOnItsNaturalKeyOrder() {
        long storeId = 812L;
        var data = bulkData(501, false);
        Long uploadId = createValidatingUpload(storeId, "bulk");
        AtomicInteger maxOrders = new AtomicInteger();
        AtomicInteger maxItems = new AtomicInteger();
        var bounded = new SalesOrderBulkInsertRepository(jdbcTemplate) {
            @Override
            public int insertOrders(List<SalesOrderEntity> batch) {
                maxOrders.accumulateAndGet(batch.size(), Math::max);
                return super.insertOrders(batch);
            }
            @Override
            public int insertItems(List<com.memme.entity.sales.SalesOrderItemEntity> batch) {
                maxItems.accumulateAndGet(batch.size(), Math::max);
                return super.insertItems(batch);
            }
        };
        var result = serviceWith(bounded, new AtomicInteger()).replaceCoverage(uploadId, storeId, data);
        assertThat(maxOrders).hasValue(SalesUploadPersistenceService.WRITE_BUFFER_SIZE);
        assertThat(maxItems).hasValue(SalesUploadPersistenceService.WRITE_BUFFER_SIZE);
        assertThat(result.appliedRecordCount()).isEqualTo(1002);
        var orders = orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(uploadId);
        assertThat(orders).hasSize(501);
        for (int i = 0; i < orders.size(); i++) {
            var order = orders.get(i);
            LocalDateTime storedAt = jdbcTemplate.queryForObject("SELECT ordered_at FROM sales_orders WHERE id=?",
                    (rs, row) -> rs.getObject(1, LocalDateTime.class), order.getId());
            int inputIndex = (int) java.time.Duration.between(data.periodStart().atStartOfDay(), storedAt).getSeconds() * 2
                    + (order.getChannel() == com.memme.entity.sales.SalesOrderChannel.POS ? 0 : 1);
            assertThat(order.getPosOrderNo()).isEqualTo("shared-order-number");
            assertThat(itemRepository.findAllBySalesOrderIdOrderByIdAsc(order.getId()))
                    .extracting(com.memme.entity.sales.SalesOrderItemEntity::getMenuName)
                    .containsExactly("menu-" + inputIndex, "extra-" + inputIndex);
        }
        var summary = dailySummaryRepository.findByStoreIdAndSalesDate(storeId, data.periodStart()).orElseThrow();
        assertThat(summary.getTotalNetAmount()).isEqualTo(1_002_000);
        assertThat(summary.getMenuNetAmount()).isEqualTo(1_002_000);
        assertThat(summary.getMenuQuantity()).isEqualTo(1002);
        assertThat(summary.getOrderCount()).isEqualTo(501);
        assertThat(summary.getSalesActivityCount()).isEqualTo(501);
    }

    @Test
    void failureAfterAnItemBatchRestoresPreviousCoverageAndSummary() {
        long storeId = 813L;
        var original = workbookData();
        Long originalUpload = createValidatingUpload(storeId, "original");
        persistenceService.replaceCoverage(originalUpload, storeId, original);
        var before = orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(originalUpload)
                .stream().map(com.memme.entity.sales.SalesOrderEntity::getId).toList();
        Long failingUpload = createValidatingUpload(storeId, "invalid");
        assertThatThrownBy(() -> persistenceService.replaceCoverage(failingUpload, storeId, bulkData(501, true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("linePrice");
        assertThat(orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(failingUpload)).isEmpty();
        assertThat(orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(originalUpload))
                .extracting(com.memme.entity.sales.SalesOrderEntity::getId).containsExactlyElementsOf(before);
        assertThat(itemRepository.countCurrentItemsByStoreId(storeId)).isEqualTo(3);
        assertThat(dailySummaryRepository.findByStoreIdAndSalesDate(storeId, original.periodStart())
                .orElseThrow().getTotalNetAmount()).isEqualTo(1000);
    }

    private TossPosWorkbookData bulkData(int count, boolean invalidLast) {
        var date = LocalDate.of(2026, 9, 1);
        List<SalesOrder> orders = new ArrayList<>();
        List<SalesOrderItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var time = date.atStartOfDay().plusSeconds(i / 2);
            var channel = i % 2 == 0 ? SalesChannel.POS : SalesChannel.KIOSK;
            var first = item(date, SalesOrderStatus.COMPLETED, channel, "shared-order-number", time,
                    "menu-" + i, 1, 1000, 1000, 1000);
            var extra = item(date, SalesOrderStatus.COMPLETED, channel, "shared-order-number", time,
                    "extra-" + i, 1, invalidLast && i == count - 1 ? 999 : 1000, 1000, 1000);
            orders.add(new SalesOrder(first.orderKey(), true, List.of(first, extra)));
            items.add(first);
            items.add(extra);
        }
        return new TossPosWorkbookData(date, date, orders, items, List.of());
    }

    private Long createValidatingUpload(long storeId, String suffix) {
        SalesUploadEntity upload = SalesUploadEntity.pending(
                storeId,
                7L,
                "sales.xlsx",
                "sales/" + storeId + "/" + suffix + ".xlsx",
                (suffix.equals("first") ? "a" : "b").repeat(64)
        );
        upload.startProcessing();
        return uploadRepository.saveAndFlush(upload).getId();
    }

    private TossPosWorkbookData workbookData() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        SalesOrderItem completed = item(
                date,
                SalesOrderStatus.COMPLETED,
                SalesChannel.POS,
                "000123",
                LocalDateTime.of(2026, 9, 1, 9, 0),
                "아메리카노",
                2,
                2_000,
                1_000,
                2_000
        );
        SalesOrderItem canceled = item(
                date,
                SalesOrderStatus.CANCELED,
                SalesChannel.POS,
                "000124",
                LocalDateTime.of(2026, 9, 1, 10, 0),
                "아메리카노",
                -1,
                -1_000,
                1_000,
                -1_000
        );
        SalesOrderItem zeroWon = item(
                date,
                SalesOrderStatus.COMPLETED,
                SalesChannel.KIOSK,
                "000125",
                LocalDateTime.of(2026, 9, 1, 11, 0),
                "이벤트 아메리카노",
                1,
                0,
                0,
                0
        );
        List<SalesOrderItem> items = List.of(completed, canceled, zeroWon);
        List<SalesOrder> orders = List.of(
                new SalesOrder(completed.orderKey(), true, List.of(completed)),
                new SalesOrder(canceled.orderKey(), false, List.of(canceled)),
                new SalesOrder(zeroWon.orderKey(), true, List.of(zeroWon))
        );
        return new TossPosWorkbookData(
                date,
                date,
                orders,
                items,
                List.of(new DailySalesSummary(date, 1_000, 1_000, 2, 2, SalesDailyStatus.COMPLETE))
        );
    }

    private SalesOrderItem item(
            LocalDate date,
            SalesOrderStatus status,
            SalesChannel channel,
            String orderNo,
            LocalDateTime orderedAt,
            String menuName,
            int quantity,
            long linePrice,
            long unitPrice,
            long netAmount
    ) {
        return item(date, status, channel, orderNo, orderedAt, menuName, quantity, linePrice, unitPrice, netAmount,
                SalesItemType.MENU);
    }

    private SalesOrderItem item(
            LocalDate date,
            SalesOrderStatus status,
            SalesChannel channel,
            String orderNo,
            LocalDateTime orderedAt,
            String menuName,
            int quantity,
            long linePrice,
            long unitPrice,
            long netAmount,
            SalesItemType itemType
    ) {
        return new SalesOrderItem(
                new SalesOrderKey(channel, orderNo, orderedAt),
                date,
                status,
                menuName,
                menuName,
                menuName,
                "커피",
                MenuCategory.COFFEE,
                "",
                quantity,
                linePrice,
                unitPrice,
                0,
                "",
                0,
                "",
                0,
                netAmount,
                true,
                netAmount == 0 ? 0 : Math.round(netAmount / 11.0),
                itemType
        );
    }
}
