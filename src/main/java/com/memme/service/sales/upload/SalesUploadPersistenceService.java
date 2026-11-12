package com.memme.service.sales.upload;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.sales.SalesDailyStatus;
import com.memme.entity.sales.SalesOrderChannel;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.SalesOrderItemEntity;
import com.memme.entity.sales.SalesOrderItemStatus;
import com.memme.entity.sales.SalesOrderItemType;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.sales.SalesOrderBulkInsertRepository;
import com.memme.repository.sales.SalesOrderBulkInsertRepository.OrderNaturalKey;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.service.sales.DailySalesSummary;
import com.memme.service.sales.SalesOrder;
import com.memme.service.sales.SalesOrderItem;
import com.memme.service.sales.TossPosWorkbookData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SalesUploadPersistenceService {

    static final int MAX_LOCK_RETRIES = 2;
    static final int WRITE_BUFFER_SIZE = 500;
    private static final Logger log = LoggerFactory.getLogger(SalesUploadPersistenceService.class);

    private final SalesUploadRepository uploadRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderItemRepository itemRepository;
    private final SalesDailySummaryRepository dailySummaryRepository;
    private final StoreBusinessHoursRepository businessHoursRepository;
    private final SalesDailyStatusResolver dailyStatusResolver;
    private final SalesOrderBulkInsertRepository bulkInsertRepository;
    private final TransactionTemplate transactionTemplate;
    private final LockRetryBackoff retryBackoff;

    public SalesUploadPersistenceService(
            SalesUploadRepository uploadRepository,
            SalesOrderRepository orderRepository,
            SalesOrderItemRepository itemRepository,
            SalesDailySummaryRepository dailySummaryRepository,
            StoreBusinessHoursRepository businessHoursRepository,
            SalesDailyStatusResolver dailyStatusResolver,
            SalesOrderBulkInsertRepository bulkInsertRepository,
            PlatformTransactionManager transactionManager,
            LockRetryBackoff retryBackoff
    ) {
        this.uploadRepository = uploadRepository;
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.dailySummaryRepository = dailySummaryRepository;
        this.businessHoursRepository = businessHoursRepository;
        this.dailyStatusResolver = dailyStatusResolver;
        this.bulkInsertRepository = bulkInsertRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.retryBackoff = retryBackoff;
    }

    /**
     * 업로드 파일 기간의 기존 주문을 지우고 새 주문·항목·일별 집계를 한 트랜잭션으로 반영한다.
     * 바깥 트랜잭션이 없으면(웹 Worker 경로) 락 대기 초과·교착 시 짧게 쉬고 다시 시도한다.
     * 바깥 트랜잭션 안(Outbox 경로)에서는 한 번만 실행하고 재시도는 Outbox 재시도 정책에 맡긴다.
     */
    public SalesUploadResult replaceCoverage(Long uploadId, Long storeId, TossPosWorkbookData workbookData) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return replaceCoverageOnce(uploadId, storeId, workbookData);
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionTemplate.execute(status -> replaceCoverageOnce(uploadId, storeId, workbookData));
            } catch (CannotAcquireLockException exception) {
                if (attempt > MAX_LOCK_RETRIES) {
                    throw exception;
                }
                log.warn("Sales upload coverage lock conflict, retrying uploadId={} attempt={}/{}",
                        uploadId, attempt, MAX_LOCK_RETRIES);
                retryBackoff.pause(attempt);
            }
        }
    }

    private SalesUploadResult replaceCoverageOnce(Long uploadId, Long storeId, TossPosWorkbookData workbookData) {
        SalesUploadEntity upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalStateException("매출 업로드를 찾을 수 없습니다: " + uploadId));
        if (!upload.getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("업로드의 매장 정보가 일치하지 않습니다.");
        }

        deleteExistingCoverage(storeId, workbookData.periodStart(), workbookData.periodEnd());

        List<SalesOrder> orders = workbookData.orders();
        for (int start = 0; start < orders.size(); start += WRITE_BUFFER_SIZE) {
            int end = Math.min(start + WRITE_BUFFER_SIZE, orders.size());
            bulkInsertRepository.insertOrders(orders.subList(start, end).stream()
                    .map(order -> toEntity(uploadId, storeId, order)).toList());
        }
        Map<OrderNaturalKey, Long> orderIds = bulkInsertRepository.findOrderIdsBySalesUploadId(uploadId);
        if (orderIds.size() != orders.size()) {
            throw new IllegalStateException("저장된 주문 건수가 일치하지 않습니다.");
        }

        int appliedRecordCount = 0;
        List<SalesOrderItemEntity> pendingItems = new ArrayList<>(WRITE_BUFFER_SIZE);
        for (SalesOrder order : orders) {
            OrderNaturalKey key = new OrderNaturalKey(storeId,
                    SalesOrderChannel.valueOf(order.key().channel().name()),
                    order.key().posOrderNo(), order.key().orderedAt());
            Long orderId = orderIds.get(key);
            if (orderId == null) {
                throw new IllegalStateException("저장한 매출 주문의 ID를 찾을 수 없습니다: " + order.key().posOrderNo());
            }
            for (SalesOrderItem item : order.items()) {
                pendingItems.add(toEntity(orderId, item));
                if (pendingItems.size() == WRITE_BUFFER_SIZE) {
                    appliedRecordCount = Math.addExact(appliedRecordCount, bulkInsertRepository.insertItems(pendingItems));
                    pendingItems.clear();
                }
            }
        }
        if (!pendingItems.isEmpty()) {
            appliedRecordCount = Math.addExact(appliedRecordCount, bulkInsertRepository.insertItems(pendingItems));
        }

        rebuildDailySummaries(storeId, workbookData);

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
                item.category(),
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

    private void rebuildDailySummaries(Long storeId, TossPosWorkbookData workbookData) {
        LocalDate periodStart = workbookData.periodStart();
        LocalDate periodEnd = workbookData.periodEnd();
        Map<LocalDate, MutableDailySummary> summaries = new TreeMap<>();
        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            summaries.put(date, new MutableDailySummary());
        }
        for (SalesOrder order : workbookData.orders()) {
            MutableDailySummary summary = summaryOf(summaries, order);
            summary.salesActivityCount = Math.incrementExact(summary.salesActivityCount);
            if (order.valid()) summary.orderCount = Math.incrementExact(summary.orderCount);
            for (SalesOrderItem item : order.items()) {
                summary.totalNetAmount = Math.addExact(summary.totalNetAmount, item.netAmount());
                if (item.itemType() == com.memme.service.sales.SalesItemType.MENU) {
                    summary.menuNetAmount = Math.addExact(summary.menuNetAmount, item.netAmount());
                    summary.menuQuantity = Math.addExact(summary.menuQuantity, item.quantity());
                }
            }
        }

        Map<LocalDate, DailySalesSummary> parsedSummariesByDate = new HashMap<>();
        workbookData.dailySummaries().forEach(summary -> parsedSummariesByDate.put(summary.salesDate(), summary));
        Map<Integer, StoreBusinessHours> businessHoursByDayOfWeek = new HashMap<>();
        for (StoreBusinessHours businessHours : businessHoursRepository
                .findAllByStoreIdOrderByDayOfWeekAsc(storeId)) {
            businessHoursByDayOfWeek.put(businessHours.getDayOfWeek(), businessHours);
        }

        Map<LocalDate, Boolean> hasOrderRowsByDate = new HashMap<>();
        workbookData.items().forEach(item -> hasOrderRowsByDate.put(item.orderDate(), true));
        Map<LocalDate, SalesDailySummaryEntity> existingByDate = new HashMap<>();
        dailySummaryRepository.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                storeId,
                periodStart,
                periodEnd
        ).forEach(summary -> existingByDate.put(summary.getSalesDate(), summary));

        List<SalesDailySummaryEntity> changedSummaries = new ArrayList<>(summaries.size());
        summaries.forEach((date, values) -> {
            SalesDailySummaryEntity summary = existingByDate.get(date);
            StoreBusinessHours businessHours = businessHoursByDayOfWeek.get(date.getDayOfWeek().getValue());
            Boolean storeClosed = businessHours == null ? null : dailyStatusResolver.confirmedClosedStatus(
                    date, businessHours.getUpdatedAt(), businessHours.isClosed()
            );
            DailySalesSummary parsedSummary = parsedSummariesByDate.get(date);
            boolean hasOrderRows = hasOrderRowsByDate.containsKey(date);
            boolean zeroSalesConfirmed = parsedSummary != null
                    && parsedSummary.status() == SalesDailyStatus.COMPLETE
                    && !hasOrderRows;
            SalesDailyStatus dayStatus = dailyStatusResolver.resolve(
                    hasOrderRows, zeroSalesConfirmed, storeClosed
            );
            if (summary == null) {
                summary = SalesDailySummaryEntity.create(
                        storeId,
                        date,
                        values.totalNetAmount,
                        values.menuNetAmount,
                        values.orderCount,
                        values.menuQuantity,
                        values.salesActivityCount,
                        dayStatus
                );
            } else {
                summary.update(
                        values.totalNetAmount,
                        values.menuNetAmount,
                        values.orderCount,
                        values.menuQuantity,
                        values.salesActivityCount,
                        dayStatus
                );
            }
            changedSummaries.add(summary);
        });
        dailySummaryRepository.saveAll(changedSummaries);
    }

    private static MutableDailySummary summaryOf(Map<LocalDate, MutableDailySummary> summaries, SalesOrder order) {
        MutableDailySummary summary = summaries.get(order.key().orderedAt().toLocalDate());
        if (summary == null) {
            throw new IllegalStateException("주문일이 파일 기간을 벗어났습니다: " + order.key().posOrderNo());
        }
        return summary;
    }

    private static final class MutableDailySummary {
        private long totalNetAmount;
        private long menuNetAmount;
        private int orderCount;
        private int menuQuantity;
        private int salesActivityCount;
    }
}
