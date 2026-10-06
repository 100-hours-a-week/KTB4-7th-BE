package com.memme.service.store;

import com.memme.entity.store.StoreCostItem;
import com.memme.repository.store.StoreCostItemRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreCostCarryForwardService {

    private final StoreCostItemRepository costItemRepository;
    private final Clock clock;

    public StoreCostCarryForwardService(StoreCostItemRepository costItemRepository, Clock clock) {
        this.costItemRepository = costItemRepository;
        this.clock = clock;
    }

    @Transactional
    public void copyForSalesMonths(Long storeId, List<YearMonth> salesMonths) {
        for (YearMonth month : salesMonths.stream().distinct().sorted().toList()) {
            if (costItemRepository.findByStoreIdAndCostMonth(storeId, month.atDay(1)).isPresent()) {
                continue;
            }
            costItemRepository.findFirstByStoreIdAndCostMonthLessThanOrderByCostMonthDesc(
                    storeId, month.atDay(1)).ifPresent(previous -> costItemRepository.save(
                            StoreCostItem.create(storeId, month.atDay(1), previous.getRentAmount(),
                                    previous.getLaborAmount(), previous.getIngredientCostRate(),
                                    LocalDateTime.now(clock))));
        }
    }
}
