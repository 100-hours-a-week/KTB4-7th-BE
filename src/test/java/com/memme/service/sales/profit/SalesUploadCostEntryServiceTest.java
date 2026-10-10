package com.memme.service.sales.profit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.dto.sales.SalesUploadCostSaveRequest;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.entity.sales.SalesOrderEntity;
import com.memme.entity.sales.StoreCostItem;
import com.memme.exception.sales.SalesUploadCostEntryException;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.sales.StoreCostItemRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesUploadCostEntryServiceTest {

    @Mock SalesUploadRepository uploadRepository;
    @Mock SalesOrderRepository orderRepository;
    @Mock StoreCostItemRepository costRepository;
    @Mock StoreOwnershipRepository ownershipRepository;
    private SalesUploadCostEntryService service;

    @BeforeEach
    void setUp() {
        service = new SalesUploadCostEntryService(uploadRepository, orderRepository, costRepository, ownershipRepository,
                Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    void 저장된_월은_그_값을_보여주고_다음_월에는_미저장_제안으로만_표시한다() {
        completedUpload(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));
        StoreCostItem august = cost(LocalDate.of(2026, 8, 1), 1_500_000, "0.4");
        when(costRepository.findAllByStoreIdAndCostMonthBetweenOrderByCostMonthAsc(2L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1))).thenReturn(List.of(august));

        var result = service.get(1L, 2L, 3L);

        assertThat(result.months()).hasSize(2);
        assertThat(result.months().get(0).source()).isEqualTo("SAVED");
        assertThat(result.months().get(1).source()).isEqualTo("SUGGESTED");
        assertThat(result.months().get(1).ingredientCostRate()).isEqualByComparingTo("40");
        verify(costRepository, never()).saveAll(any());
    }

    @Test
    void 이전_확정값이_없으면_입력칸이_비어있다() {
        completedUpload(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        var result = service.get(1L, 2L, 3L);

        assertThat(result.months().getFirst().source()).isEqualTo("EMPTY");
        assertThat(result.months().getFirst().rentAmount()).isNull();
    }

    @Test
    void 매출_행이_있으면_파일_기간_중_실제_발생한_월만_표시한다() {
        completedUpload(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31));
        SalesOrderEntity august = mock(SalesOrderEntity.class);
        SalesOrderEntity october = mock(SalesOrderEntity.class);
        when(august.getOrderedAt()).thenReturn(LocalDateTime.of(2026, 8, 2, 10, 0));
        when(october.getOrderedAt()).thenReturn(LocalDateTime.of(2026, 10, 2, 10, 0));
        when(orderRepository.findAllBySalesUploadIdOrderByOrderedAtAsc(3L))
                .thenReturn(List.of(august, october));

        var result = service.get(1L, 2L, 3L);

        assertThat(result.months()).extracting(month -> month.costMonth())
                .containsExactly("2026-08", "2026-10");
    }

    @Test
    void 여러_월을_한번에_저장하고_원가율을_DB_비율로_바꾼다() {
        completedUpload(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));
        var request = new SalesUploadCostSaveRequest(List.of(
                item("2026-08", "40"), item("2026-09", "35.5")));

        var result = service.put(1L, 2L, 3L, request);

        assertThat(result.costMonths()).containsExactly("2026-08", "2026-09");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StoreCostItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(costRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(StoreCostItem::getIngredientCostRate)
                .containsExactly(new BigDecimal("0.40"), new BigDecimal("0.355"));
    }

    @Test
    void 파일과_다른_월_집합은_저장하지_않는다() {
        completedUpload(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));
        var request = new SalesUploadCostSaveRequest(List.of(item("2026-08", "40")));

        assertThatThrownBy(() -> service.put(1L, 2L, 3L, request))
                .isInstanceOf(SalesUploadCostEntryException.class);
        verify(costRepository, never()).saveAll(any());
    }

    @Test
    void 처리_중인_업로드는_비용_입력에_사용할_수_없다() {
        when(ownershipRepository.existsActiveStoreOwnedBy(2L, 1L)).thenReturn(true);
        SalesUploadEntity upload = mock(SalesUploadEntity.class);
        when(upload.getStoreId()).thenReturn(2L);
        when(upload.getStatus()).thenReturn(SalesUploadStatus.PROCESSING);
        when(uploadRepository.findById(3L)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.get(1L, 2L, 3L))
                .isInstanceOf(SalesUploadCostEntryException.class)
                .hasMessageContaining("완료되지");
    }

    @Test
    void 다른_매장의_업로드는_조회할_수_없다() {
        when(ownershipRepository.existsActiveStoreOwnedBy(2L, 1L)).thenReturn(true);
        SalesUploadEntity upload = mock(SalesUploadEntity.class);
        when(upload.getStoreId()).thenReturn(9L);
        when(uploadRepository.findById(3L)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.get(1L, 2L, 3L))
                .isInstanceOf(SalesUploadCostEntryException.class)
                .hasMessageContaining("찾을 수 없습니다");
        verify(costRepository, never()).saveAll(any());
    }

    private void completedUpload(LocalDate start, LocalDate end) {
        when(ownershipRepository.existsActiveStoreOwnedBy(2L, 1L)).thenReturn(true);
        SalesUploadEntity upload = mock(SalesUploadEntity.class);
        when(upload.getId()).thenReturn(3L);
        when(upload.getStoreId()).thenReturn(2L);
        when(upload.getStatus()).thenReturn(SalesUploadStatus.COMPLETED);
        when(upload.getPeriodStart()).thenReturn(start);
        when(upload.getPeriodEnd()).thenReturn(end);
        when(uploadRepository.findById(3L)).thenReturn(Optional.of(upload));
    }

    private StoreCostItem cost(LocalDate month, long rent, String rate) {
        return StoreCostItem.create(2L, month, rent, 2_000_000, new BigDecimal(rate),
                LocalDateTime.of(2026, 8, 2, 12, 0));
    }

    private SalesUploadCostSaveRequest.Item item(String month, String rate) {
        return new SalesUploadCostSaveRequest.Item(month, new BigDecimal("1500000"),
                new BigDecimal("3000000"), new BigDecimal(rate));
    }
}
