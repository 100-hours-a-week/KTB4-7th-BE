package com.memme.service.sales.upload;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
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
import com.memme.service.sales.StandardMenuCategory;
import com.memme.service.sales.TossPosWorkbookData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SalesUploadPersistenceServiceTest {

    private final SalesUploadPersistenceService persistenceService;
    private final SalesUploadRepository uploadRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final SalesDailySummaryRepository dailySummaryRepository;

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
        assertThat(summary.getMenuQuantity()).isEqualTo(2);
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
                List.of(new DailySalesSummary(date, 1_000, 1_000, 2, 2))
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
        return new SalesOrderItem(
                new SalesOrderKey(channel, orderNo, orderedAt),
                date,
                status,
                menuName,
                menuName,
                menuName,
                "커피",
                StandardMenuCategory.COFFEE,
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
                SalesItemType.MENU
        );
    }
}
