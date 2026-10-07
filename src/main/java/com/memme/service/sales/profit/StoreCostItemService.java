package com.memme.service.sales.profit;

import com.memme.dto.sales.StoreCostItemGetData;
import com.memme.dto.sales.StoreCostItemPutData;
import com.memme.dto.sales.StoreCostItemPutResult;
import com.memme.dto.sales.StoreCostItemRequest;
import com.memme.dto.sales.StoreCostItemResponse;
import com.memme.entity.sales.StoreCostItem;
import com.memme.exception.sales.InvalidStoreCostMonthException;
import com.memme.exception.store.StoreProfileNotFoundException;
import com.memme.repository.sales.StoreCostItemRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreCostItemService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final StoreCostItemRepository costItemRepository;
    private final StoreOwnershipRepository ownershipRepository;
    private final Clock clock;

    public StoreCostItemService(StoreCostItemRepository costItemRepository,
            StoreOwnershipRepository ownershipRepository, Clock clock) {
        this.costItemRepository = costItemRepository;
        this.ownershipRepository = ownershipRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StoreCostItemGetData get(Long userId, Long storeId, String costMonth) {
        YearMonth month = parseMonth(costMonth);
        verifyOwner(userId, storeId);
        return new StoreCostItemGetData(costItemRepository.findByStoreIdAndCostMonth(storeId, month.atDay(1))
                .map(this::toResponse)
                .orElse(null));
    }

    @Transactional
    public StoreCostItemPutResult put(Long userId, Long storeId, String costMonth, StoreCostItemRequest request) {
        YearMonth month = parseMonth(costMonth);
        verifyOwner(userId, storeId);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), KOREA_ZONE);
        StoreCostItem item = costItemRepository.findByStoreIdAndCostMonth(storeId, month.atDay(1)).orElse(null);
        boolean created = item == null;
        if (created) {
            item = StoreCostItem.create(storeId, month.atDay(1), request.rentAmount().longValueExact(),
                    request.laborAmount().longValueExact(), request.ingredientCostRate(), now);
        } else {
            item.update(request.rentAmount().longValueExact(), request.laborAmount().longValueExact(),
                    request.ingredientCostRate(), now);
        }
        StoreCostItem saved = costItemRepository.save(item);
        return new StoreCostItemPutResult(new StoreCostItemPutData(toResponse(saved)), created);
    }

    private void verifyOwner(Long userId, Long storeId) {
        if (!ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new StoreProfileNotFoundException();
        }
    }

    private YearMonth parseMonth(String costMonth) {
        try {
            if (costMonth == null || !costMonth.matches("\\d{4}-(0[1-9]|1[0-2])")) {
                throw new InvalidStoreCostMonthException();
            }
            return YearMonth.parse(costMonth);
        } catch (DateTimeException exception) {
            throw new InvalidStoreCostMonthException();
        }
    }

    private StoreCostItemResponse toResponse(StoreCostItem item) {
        return new StoreCostItemResponse(YearMonth.from(item.getCostMonth()).toString(),
                item.getRentAmount(), item.getLaborAmount(), item.getIngredientCostRate(),
                item.getUpdatedAt().atZone(KOREA_ZONE).toOffsetDateTime());
    }
}
