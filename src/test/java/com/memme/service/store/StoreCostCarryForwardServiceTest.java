package com.memme.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.entity.store.StoreCostItem;
import com.memme.repository.store.StoreCostItemRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreCostCarryForwardServiceTest {

    @Mock private StoreCostItemRepository repository;

    @Test
    void 여러_매출_월을_오래된_순서로_복사하고_기존_월은_덮어쓰지_않는다() {
        Map<LocalDate, StoreCostItem> rows = new HashMap<>();
        rows.put(LocalDate.of(2026, 8, 1), cost(2026, 8, 100));
        rows.put(LocalDate.of(2026, 10, 1), cost(2026, 10, 300));
        when(repository.findByStoreIdAndCostMonth(eq(20L), any()))
                .thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(1))));
        when(repository.findFirstByStoreIdAndCostMonthLessThanOrderByCostMonthDesc(eq(20L), any()))
                .thenAnswer(call -> {
                    LocalDate month = call.getArgument(1);
                    return rows.entrySet().stream().filter(entry -> entry.getKey().isBefore(month))
                            .max(Map.Entry.comparingByKey()).map(Map.Entry::getValue);
                });
        when(repository.save(any(StoreCostItem.class))).thenAnswer(call -> {
            StoreCostItem item = call.getArgument(0);
            rows.put(item.getCostMonth(), item);
            return item;
        });
        StoreCostCarryForwardService service = new StoreCostCarryForwardService(repository,
                Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneId.of("Asia/Seoul")));

        service.copyForSalesMonths(20L, List.of(YearMonth.of(2026, 11), YearMonth.of(2026, 9),
                YearMonth.of(2026, 10), YearMonth.of(2026, 11)));

        assertThat(rows.get(LocalDate.of(2026, 9, 1)).getRentAmount()).isEqualTo(100);
        assertThat(rows.get(LocalDate.of(2026, 10, 1)).getRentAmount()).isEqualTo(300);
        assertThat(rows.get(LocalDate.of(2026, 11, 1)).getRentAmount()).isEqualTo(300);
        assertThat(rows).hasSize(4);
    }

    @Test
    void 앞선_비용_입력이_없으면_비용_행을_만들지_않는다() {
        StoreCostCarryForwardService service = new StoreCostCarryForwardService(repository,
                Clock.system(ZoneId.of("Asia/Seoul")));

        service.copyForSalesMonths(20L, List.of(YearMonth.of(2026, 10)));

        verify(repository, never()).save(any());
    }

    private StoreCostItem cost(int year, int month, long rent) {
        return StoreCostItem.create(20L, LocalDate.of(year, month, 1), rent, 0,
                new BigDecimal("0.4"), LocalDateTime.of(2026, month, 1, 9, 0));
    }
}
