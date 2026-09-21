package com.memme.service.sales.upload;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesOrderChannel;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemStatus;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.sales.SalesStandardMenuCategory;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.service.sales.SalesOrder;
import com.memme.service.sales.SalesOrderItem;
import com.memme.service.sales.TossPosWorkbookData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesUploadPersistenceService {

    private final SalesUploadRepository uploadRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final SalesDailySummaryRepository dailySummaryRepository;

    public SalesUploadPersistenceService(
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

    @Transactional
    public SalesUploadResult replaceCoverage(Long uploadId, Long storeId, TossPosWorkbookData workbookData) {
        SalesUploadEntity upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalStateException("매출 업로드를 찾을 수 없습니다: " + uploadId));
        if (!upload.getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("업로드의 매장 정보가 일치하지 않습니다.");
        }

        deleteExistingCoverage(storeId, workbookData.periodStart(), workbookData.periodEnd());

        int appliedRecordCount = 0;
        for (SalesOrder order : workbookData.orders()) {
            SalesOrderEntity savedOrder = orderRepository.save(toEntity(uploadId, storeId, order));
            List<SalesOrderItemEntity> savedItems = order.items().stream()
                    .map(item -> toEntity(savedOrder.getId(), item))
                    .toList();
            itemRepository.saveAll(savedItems);
            appliedRecordCount = Math.addExact(appliedRecordCount, savedItems.size());
        }

        rebuildDailySummaries(storeId, workbookData.periodStart(), workbookData.periodEnd());

        return new SalesUploadResult(
                upload.getId(),
                workbookData.periodStart(),
                workbookData.periodEnd(),
                workbookData.items().size(),
                appliedRecordCount
        );
    }

    private void deleteExistingCoverage(
            Long storeId,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        LocalDateTime start = periodStart.atStartOfDay();
        LocalDateTime endExclusive = periodEnd.plusDays(1).atStartOfDay();
        List<SalesOrderEntity> existingOrders = orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        storeId,
                        start,
                        endExclusive
                );
        if (existingOrders.isEmpty()) {
            return;
        }
        List<Long> orderIds = existingOrders.stream().map(SalesOrderEntity::getId).toList();
        itemRepository.deleteAllInBatch(
                itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(orderIds)
        );
        orderRepository.deleteAllInBatch(existingOrders);
        orderRepository.flush();
    }

    private SalesOrderEntity toEntity(Long uploadId, Long storeId, SalesOrder order) {
        return SalesOrderEntity.create(
                uploadId,
                storeId,
                SalesOrderChannel.valueOf(order.key().channel().name()),
                order.key().posOrderNo(),
                order.key().orderedAt(),
                order.valid()
        );
    }

    private SalesOrderItemEntity toEntity(Long orderId, SalesOrderItem item) {
        return SalesOrderItemEntity.create(new SalesOrderItemEntity.Values(
                orderId,
                null,
                SalesOrderItemStatus.valueOf(item.status().name()),
                item.menuNameRaw(),
                item.menuName(),
                item.menuKey(),
                item.categoryRaw(),
                SalesStandardMenuCategory.valueOf(item.category().name()),
                item.optionRaw(),
                item.quantity(),
                item.linePrice(),
                item.unitPrice(),
                item.optionPrice(),
                item.itemDiscountName(),
                item.itemDiscountAmount(),
                item.orderDiscountName(),
                item.orderDiscountAmount(),
                item.netAmount(),
                item.taxable(),
                item.vatAmount(),
                SalesOrderItemType.valueOf(item.itemType().name())
        ));
    }

    private void rebuildDailySummaries(Long storeId, LocalDate periodStart, LocalDate periodEnd) {
        List<SalesOrderEntity> orders = orderRepository
                .findAllByStoreIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtAsc(
                        storeId,
                        periodStart.atStartOfDay(),
                        periodEnd.plusDays(1).atStartOfDay()
                );
        List<Long> orderIds = orders.stream().map(SalesOrderEntity::getId).toList();
        List<SalesOrderItemEntity> items = orderIds.isEmpty()
                ? List.of()
                : itemRepository.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(orderIds);

        Map<Long, SalesOrderEntity> ordersById = new HashMap<>();
        for (SalesOrderEntity order : orders) {
            ordersById.put(order.getId(), order);
        }

        Map<LocalDate, MutableDailySummary> summaries = new TreeMap<>();
        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            summaries.put(date, new MutableDailySummary());
        }
        for (SalesOrderItemEntity item : items) {
            SalesOrderEntity order = ordersById.get(item.getSalesOrderId());
            if (order == null) {
                throw new IllegalStateException("주문 항목의 상위 주문을 찾을 수 없습니다: " + item.getId());
            }
            MutableDailySummary summary = summaries.get(order.getOrderedAt().toLocalDate());
            summary.totalNetAmount = Math.addExact(summary.totalNetAmount, item.getNetAmount());
            if (item.getItemType() == SalesOrderItemType.MENU) {
                summary.menuNetAmount = Math.addExact(summary.menuNetAmount, item.getNetAmount());
                summary.menuQuantity = Math.addExact(summary.menuQuantity, item.getQuantity());
            }
        }
        for (SalesOrderEntity order : orders) {
            if (order.isValid()) {
                MutableDailySummary summary = summaries.get(order.getOrderedAt().toLocalDate());
                summary.orderCount = Math.incrementExact(summary.orderCount);
            }
        }

        Map<LocalDate, SalesDailySummaryEntity> existingByDate = new HashMap<>();
        dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId,
                periodStart,
                periodEnd
        ).forEach(summary -> existingByDate.put(summary.getSalesDate(), summary));

        List<SalesDailySummaryEntity> changedSummaries = new ArrayList<>(summaries.size());
        summaries.forEach((date, values) -> {
            SalesDailySummaryEntity summary = existingByDate.get(date);
            if (summary == null) {
                summary = SalesDailySummaryEntity.create(
                        storeId,
                        date,
                        values.totalNetAmount,
                        values.menuNetAmount,
                        values.orderCount,
                        values.menuQuantity
                );
            } else {
                summary.update(
                        values.totalNetAmount,
                        values.menuNetAmount,
                        values.orderCount,
                        values.menuQuantity
                );
            }
            changedSummaries.add(summary);
        });
        dailySummaryRepository.saveAll(changedSummaries);
    }

    private static final class MutableDailySummary {
        private long totalNetAmount;
        private long menuNetAmount;
        private int orderCount;
        private int menuQuantity;
    }
}
