package com.memme.service.sales.profit;

import com.memme.dto.sales.SalesUploadCostEntryResponse;
import com.memme.dto.sales.SalesUploadCostSaveRequest;
import com.memme.dto.sales.SalesUploadCostSaveResponse;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.StoreCostItem;
import com.memme.exception.sales.SalesUploadCostEntryException;
import com.memme.exception.store.StoreProfileNotFoundException;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.sales.StoreCostItemRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesUploadCostEntryService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final SalesUploadRepository uploadRepository;
    private final SalesOrderRepository orderRepository;
    private final StoreCostItemRepository costRepository;
    private final StoreOwnershipRepository ownershipRepository;
    private final Clock clock;

    public SalesUploadCostEntryService(SalesUploadRepository uploadRepository, SalesOrderRepository orderRepository,
            StoreCostItemRepository costRepository, StoreOwnershipRepository ownershipRepository, Clock clock) {
        this.uploadRepository = uploadRepository;
        this.orderRepository = orderRepository;
        this.costRepository = costRepository;
        this.ownershipRepository = ownershipRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SalesUploadCostEntryResponse get(Long userId, Long storeId, Long uploadId) {
        SalesUploadEntity upload = completedUpload(userId, storeId, uploadId);
        List<YearMonth> months = monthsOf(upload);
        Map<YearMonth, StoreCostItem> saved = savedCosts(storeId, months);
        StoreCostItem previous = costRepository
                .findFirstByStoreIdAndCostMonthLessThanOrderByCostMonthDesc(storeId, months.getFirst().atDay(1))
                .orElse(null);
        List<SalesUploadCostEntryResponse.Month> result = new ArrayList<>(months.size());
        for (YearMonth month : months) {
            StoreCostItem current = saved.get(month);
            if (current != null) {
                previous = current;
                result.add(toMonth(month, current, "SAVED"));
            } else if (previous != null) {
                result.add(toMonth(month, previous, "SUGGESTED"));
            } else {
                result.add(new SalesUploadCostEntryResponse.Month(month.toString(), null, null, null, "EMPTY"));
            }
        }
        return new SalesUploadCostEntryResponse(uploadId, List.copyOf(result));
    }

    @Transactional
    public SalesUploadCostSaveResponse put(Long userId, Long storeId, Long uploadId,
            SalesUploadCostSaveRequest request) {
        SalesUploadEntity upload = completedUpload(userId, storeId, uploadId);
        List<YearMonth> months = monthsOf(upload);
        if (request == null || request.items() == null || request.items().size() != months.size()) {
            throw new SalesUploadCostEntryException(SalesUploadCostEntryException.Reason.INVALID_MONTHS);
        }
        Map<YearMonth, SalesUploadCostSaveRequest.Item> submitted = new HashMap<>();
        for (SalesUploadCostSaveRequest.Item item : request.items()) {
            YearMonth month;
            try {
                month = YearMonth.parse(item.costMonth());
            } catch (RuntimeException exception) {
                throw new SalesUploadCostEntryException(SalesUploadCostEntryException.Reason.INVALID_MONTHS);
            }
            if (submitted.putIfAbsent(month, item) != null) {
                throw new SalesUploadCostEntryException(SalesUploadCostEntryException.Reason.INVALID_MONTHS);
            }
        }
        if (!submitted.keySet().equals(new HashSet<>(months))) {
            throw new SalesUploadCostEntryException(SalesUploadCostEntryException.Reason.INVALID_MONTHS);
        }

        Map<YearMonth, StoreCostItem> saved = savedCosts(storeId, months);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), KOREA_ZONE);
        List<StoreCostItem> changed = new ArrayList<>(months.size());
        for (YearMonth month : months) {
            SalesUploadCostSaveRequest.Item input = submitted.get(month);
            StoreCostItem existing = saved.get(month);
            BigDecimal rate = input.ingredientCostRate().movePointLeft(2);
            if (existing == null) {
                changed.add(StoreCostItem.create(storeId, month.atDay(1), input.rentAmount().longValueExact(),
                        input.laborAmount().longValueExact(), rate, now));
            } else {
                existing.update(input.rentAmount().longValueExact(), input.laborAmount().longValueExact(), rate, now);
                changed.add(existing);
            }
        }
        costRepository.saveAll(changed);
        return new SalesUploadCostSaveResponse(uploadId, months.stream().map(YearMonth::toString).toList());
    }

    private SalesUploadEntity completedUpload(Long userId, Long storeId, Long uploadId) {
        if (!ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new StoreProfileNotFoundException();
        }
        SalesUploadEntity upload = uploadRepository.findById(uploadId)
                .filter(found -> found.getStoreId().equals(storeId))
                .orElseThrow(() -> new SalesUploadCostEntryException(
                        SalesUploadCostEntryException.Reason.UPLOAD_NOT_FOUND));
        if (upload.getStatus() != SalesUploadStatus.COMPLETED
                || upload.getPeriodStart() == null || upload.getPeriodEnd() == null) {
            throw new SalesUploadCostEntryException(SalesUploadCostEntryException.Reason.UPLOAD_NOT_COMPLETED);
        }
        return upload;
    }

    private List<YearMonth> monthsOf(SalesUploadEntity upload) {
        Set<YearMonth> orderMonths = new TreeSet<>();
        for (SalesOrderEntity order : orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(upload.getId())) {
            orderMonths.add(YearMonth.from(order.getOrderedAt()));
        }
        if (!orderMonths.isEmpty()) {
            return List.copyOf(orderMonths);
        }
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = YearMonth.from(upload.getPeriodStart());
                !month.isAfter(YearMonth.from(upload.getPeriodEnd())); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    private Map<YearMonth, StoreCostItem> savedCosts(Long storeId, List<YearMonth> months) {
        Map<YearMonth, StoreCostItem> saved = new HashMap<>();
        costRepository.findAllByStoreIdAndCostMonthBetweenOrderByCostMonthAsc(
                storeId, months.getFirst().atDay(1), months.getLast().atDay(1))
                .forEach(item -> saved.put(YearMonth.from(item.getCostMonth()), item));
        return saved;
    }

    private SalesUploadCostEntryResponse.Month toMonth(YearMonth month, StoreCostItem item, String source) {
        return new SalesUploadCostEntryResponse.Month(month.toString(), item.getRentAmount(), item.getLaborAmount(),
                item.getIngredientCostRate().movePointRight(2), source);
    }
}
