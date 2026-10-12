package com.memme.service.sales.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesAnalysisMonthOptionServiceTest {

    @Mock SalesUploadRepository uploadRepository;
    @Mock SalesOrderRepository orderRepository;
    @Mock StoreOwnershipRepository ownershipRepository;
    private SalesAnalysisMonthOptionService service;

    @BeforeEach
    void setUp() {
        service = new SalesAnalysisMonthOptionService(uploadRepository, orderRepository, ownershipRepository);
    }

    @Test
    void 월마다_가장_최근_정상_파일을_고르고_여러_달_파일도_반영한다() {
        when(ownershipRepository.existsActiveStoreOwnedBy(2L, 1L)).thenReturn(true);
        SalesUploadEntity latest = upload(12L, "new.xlsx", 3);
        SalesUploadEntity older = upload(11L, "old.xlsx", 2);
        when(uploadRepository.findAllByStoreIdAndStatusOrderByUploadedAtDescIdDesc(2L,
                SalesUploadStatus.COMPLETED)).thenReturn(List.of(latest, older));
        when(orderRepository.findDistinctUploadMonths(2L)).thenReturn(List.of(
                new Object[] {12L, 2026, 8}, new Object[] {12L, 2026, 10},
                new Object[] {11L, 2026, 7}, new Object[] {11L, 2026, 8}));

        var result = service.get(1L, 2L);

        assertThat(result.months()).extracting(option -> option.targetMonth())
                .containsExactly("2026-07", "2026-08", "2026-10");
        assertThat(result.months()).extracting(option -> option.fileName())
                .containsExactly("old.xlsx", "new.xlsx", "new.xlsx");
        assertThat(result.months().get(1).uploadId()).isEqualTo(12L);
    }

    @Test
    void 주문이_없는_정상_파일은_파일_기간의_월을_제공한다() {
        when(ownershipRepository.existsActiveStoreOwnedBy(2L, 1L)).thenReturn(true);
        SalesUploadEntity upload = upload(12L, "empty.xlsx", 3);
        when(upload.getPeriodStart()).thenReturn(LocalDate.of(2026, 8, 1));
        when(upload.getPeriodEnd()).thenReturn(LocalDate.of(2026, 9, 30));
        when(uploadRepository.findAllByStoreIdAndStatusOrderByUploadedAtDescIdDesc(2L,
                SalesUploadStatus.COMPLETED)).thenReturn(List.of(upload));

        assertThat(service.get(1L, 2L).months()).extracting(option -> option.targetMonth())
                .containsExactly("2026-08", "2026-09");
    }

    @Test
    void 매장_소유자가_아니면_선택지를_조회하지_않는다() {
        assertThatThrownBy(() -> service.get(1L, 2L))
                .isInstanceOf(SalesAnalysisRequestException.class);
        verifyNoInteractions(uploadRepository, orderRepository);
    }

    private SalesUploadEntity upload(Long id, String fileName, int uploadedDay) {
        SalesUploadEntity upload = mock(SalesUploadEntity.class);
        when(upload.getId()).thenReturn(id);
        when(upload.getOriginalFileName()).thenReturn(fileName);
        when(upload.getUploadedAt()).thenReturn(LocalDateTime.of(2026, 10, uploadedDay, 12, 0));
        return upload;
    }
}
