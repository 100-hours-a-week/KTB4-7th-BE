package com.memme.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.store.StoreCostItemRequest;
import com.memme.entity.store.StoreCostItem;
import com.memme.exception.store.InvalidStoreCostMonthException;
import com.memme.exception.store.StoreProfileNotFoundException;
import com.memme.repository.store.StoreCostItemRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreCostItemServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T06:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Mock private StoreCostItemRepository repository;
    @Mock private StoreOwnershipRepository ownershipRepository;
    private StoreCostItemService service;

    @BeforeEach
    void setUp() {
        service = new StoreCostItemService(repository, ownershipRepository, CLOCK);
    }

    @Test
    void 비용이_없는_월은_null을_반환하고_행을_만들지_않는다() {
        when(ownershipRepository.existsActiveStoreOwnedBy(20L, 10L)).thenReturn(true);

        var result = service.get(10L, 20L, "2026-09");

        assertThat(result.costItem()).isNull();
        verify(repository).findByStoreIdAndCostMonth(20L, LocalDate.of(2026, 9, 1));
        verify(repository, never()).save(any());
    }

    @Test
    void 최초_저장은_새_월_행을_만든다() {
        when(ownershipRepository.existsActiveStoreOwnedBy(20L, 10L)).thenReturn(true);
        when(repository.save(any(StoreCostItem.class))).thenAnswer(call -> call.getArgument(0));

        var result = service.put(10L, 20L, "2026-10", request());

        assertThat(result.created()).isTrue();
        assertThat(result.data().costItem().costMonth()).isEqualTo("2026-10");
        assertThat(result.data().costItem().rentAmount()).isEqualTo(1_500_000);
        assertThat(result.data().costItem().updatedAt().getOffset().toString()).isEqualTo("+09:00");
    }

    @Test
    void 같은_월_재저장은_기존_행의_값을_교체한다() {
        StoreCostItem existing = StoreCostItem.create(20L, LocalDate.of(2026, 10, 1),
                100, 200, new BigDecimal("0.1"), LocalDateTime.of(2026, 10, 1, 9, 0));
        when(ownershipRepository.existsActiveStoreOwnedBy(20L, 10L)).thenReturn(true);
        when(repository.findByStoreIdAndCostMonth(20L, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        var result = service.put(10L, 20L, "2026-10", request());

        assertThat(result.created()).isFalse();
        assertThat(existing.getLaborAmount()).isEqualTo(3_000_000);
        assertThat(existing.getIngredientCostRate()).isEqualByComparingTo("0.325");
        verify(repository).save(existing);
    }

    @Test
    void 본인_매장이_아니면_비용을_조회하거나_저장할_수_없다() {
        assertThatThrownBy(() -> service.get(10L, 20L, "2026-10"))
                .isInstanceOf(StoreProfileNotFoundException.class);
        assertThatThrownBy(() -> service.put(10L, 20L, "2026-10", request()))
                .isInstanceOf(StoreProfileNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void 월_형식이_잘못되면_거절한다() {
        assertThatThrownBy(() -> service.get(10L, 20L, "2026-13"))
                .isInstanceOf(InvalidStoreCostMonthException.class);
    }

    private StoreCostItemRequest request() {
        return new StoreCostItemRequest(new BigDecimal("1500000"), new BigDecimal("3000000"),
                new BigDecimal("0.325"));
    }
}
