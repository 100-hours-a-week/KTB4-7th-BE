package com.memme.repository.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesOrderChannel;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemStatus;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.sales.SalesStandardMenuCategory;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SalesRepositoryTest {

    private final SalesUploadRepository uploadRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final SalesDailySummaryRepository dailySummaryRepository;

    @Autowired
    SalesRepositoryTest(
            SalesUploadRepository uploadRepository,
            SalesOrderRepository orderRepository,
            SalesOrderItemRepository itemRepository,
            SalesDailySummaryRepository dailySummaryRepository
    ) {
        this.uploadRepository = uploadRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.dailySummaryRepository = dailySummaryRepository;
    }

    @Test
    void findsOverlappingUploadPeriodWithinStoreAndSumsCompletedRecords() {
        uploadRepository.save(completedUpload(
                1L, "same-checksum", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 812
        ));
        uploadRepository.save(completedUpload(
                1L, "same-checksum", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 100
        ));
        uploadRepository.save(completedUpload(
                2L, "other-store", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 999
        ));

        var august = uploadRepository.findOverlappingPeriod(
                1L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                PageRequest.of(0, 10)
        );

        assertThat(august.getContent()).singleElement()
                .extracting(SalesUploadEntity::getAppliedRecordCount)
                .isEqualTo(812L);
        assertThat(uploadRepository.sumValidRowCount(1L, SalesUploadStatus.COMPLETED))
                .isEqualTo(912);
        assertThat(uploadRepository.findAllByStoreIdAndFileChecksumOrderByUploadedAtDesc(
                1L, "same-checksum"
        )).hasSize(2);
    }

    @Test
    void checksOrderNaturalKeyWithinStoreAndUsesExclusivePeriodEnd() {
        orderRepository.save(SalesOrderEntity.create(
                11L,
                1L,
                SalesOrderChannel.POS,
                "000123",
                LocalDateTime.of(2026, 9, 1, 0, 0),
                true
        ));
        orderRepository.save(SalesOrderEntity.create(
                11L,
                1L,
                SalesOrderChannel.DELIVERY,
                "000123",
                LocalDateTime.of(2026, 9, 2, 12, 0),
                true
        ));
        orderRepository.save(SalesOrderEntity.create(
                11L,
                1L,
                SalesOrderChannel.POS,
                "999999",
                LocalDateTime.of(2026, 10, 1, 0, 0),
                true
        ));

        assertThat(orderRepository.existsByStoreIdAndChannelAndPosOrderNoAndOrderedAt(
                1L,
                SalesOrderChannel.POS,
                "000123",
                LocalDateTime.of(2026, 9, 1, 0, 0)
        )).isTrue();
        assertThat(orderRepository.existsByStoreIdAndChannelAndPosOrderNoAndOrderedAt(
                2L,
                SalesOrderChannel.POS,
                "000123",
                LocalDateTime.of(2026, 9, 1, 0, 0)
        )).isFalse();

        assertThat(orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        1L,
                        LocalDateTime.of(2026, 9, 1, 0, 0),
                        LocalDateTime.of(2026, 10, 1, 0, 0)
                ))
                .extracting(SalesOrderEntity::getChannel)
                .containsExactly(SalesOrderChannel.POS, SalesOrderChannel.DELIVERY);
    }

    @Test
    void findsItemsForOneOrMultipleOrdersInStableOrder() {
        SalesOrderItemEntity first = itemRepository.save(item(10L, "아메리카노", 2, 8_000));
        SalesOrderItemEntity second = itemRepository.save(item(11L, "카페라떼", 1, 5_000));

        assertThat(itemRepository.findAllBySalesOrderIdOrderByIdAsc(10L))
                .extracting(SalesOrderItemEntity::getId)
                .containsExactly(first.getId());
        assertThat(itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(List.of(11L, 10L)))
                .extracting(SalesOrderItemEntity::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void findsDailySummariesWithinStoreAndDateRange() {
        dailySummaryRepository.save(SalesDailySummaryEntity.create(
                1L, LocalDate.of(2026, 9, 1), 120_000, 110_000, 18, 24
        ));
        dailySummaryRepository.save(SalesDailySummaryEntity.create(
                1L, LocalDate.of(2026, 9, 2), -20_000, -20_000, 0, -2
        ));
        dailySummaryRepository.save(SalesDailySummaryEntity.create(
                2L, LocalDate.of(2026, 9, 1), 999_000, 999_000, 30, 30
        ));

        assertThat(dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                1L,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2)
        ))
                .extracting(SalesDailySummaryEntity::getTotalNetAmount)
                .containsExactly(120_000L, -20_000L);
        assertThat(dailySummaryRepository.findByStoreIdAndSalesDate(
                2L, LocalDate.of(2026, 9, 1)
        )).isPresent();
    }

    private SalesUploadEntity completedUpload(
            Long storeId,
            String checksum,
            LocalDate periodStart,
            LocalDate periodEnd,
            int appliedRecordCount
    ) {
        SalesUploadEntity upload = SalesUploadEntity.pending(
                storeId,
                7L,
                "sales.xlsx",
                "sales/" + storeId + "/" + periodStart + ".xlsx",
                checksum
        );
        upload.startProcessing();
        upload.setCoverage(periodStart, periodEnd, appliedRecordCount);
        upload.complete(appliedRecordCount);
        return upload;
    }

    private SalesOrderItemEntity item(Long orderId, String menuName, int quantity, long linePrice) {
        return SalesOrderItemEntity.create(new SalesOrderItemEntity.Values(
                orderId,
                null,
                SalesOrderItemStatus.COMPLETED,
                menuName,
                menuName,
                menuName,
                "커피",
                SalesStandardMenuCategory.COFFEE,
                null,
                quantity,
                linePrice,
                linePrice / quantity,
                0,
                null,
                0,
                null,
                0,
                linePrice,
                true,
                Math.round(linePrice / 11.0),
                SalesOrderItemType.MENU
        ));
    }
}
