package com.memme.service.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.memme.entity.sales.SeoulDistrictQuarterImportEntity;
import com.memme.entity.sales.SeoulDistrictQuarterImportStatus;
import com.memme.repository.sales.SeoulDistrictQuarterBenchmarkRepository;
import com.memme.repository.sales.SeoulDistrictQuarterImportRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SeoulDistrictQuarterImportPersistenceServiceTest {

    private final SeoulDistrictQuarterImportRepository imports = mock(SeoulDistrictQuarterImportRepository.class);
    private final SeoulDistrictQuarterBenchmarkRepository benchmarks =
            mock(SeoulDistrictQuarterBenchmarkRepository.class);
    private final SeoulDistrictQuarterImportPersistenceService service =
            new SeoulDistrictQuarterImportPersistenceService(imports, benchmarks,
                    Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void 완료된_분기는_재수집하지_않는다() {
        var imported = mock(SeoulDistrictQuarterImportEntity.class);
        when(imported.getStatus()).thenReturn(SeoulDistrictQuarterImportStatus.COMPLETED);
        when(imports.findByBasisQuarterAndIndustryCode("2026Q2", "CS100010"))
                .thenReturn(Optional.of(imported));

        assertThat(service.begin("2026Q2")).isEmpty();

        verify(imported, never()).restart(any());
        verify(imports, never()).saveAndFlush(any());
    }

    @Test
    void 실패한_분기는_다시_PROCESSING으로_시작한다() {
        var imported = mock(SeoulDistrictQuarterImportEntity.class);
        when(imported.getStatus()).thenReturn(SeoulDistrictQuarterImportStatus.FAILED);
        when(imported.getId()).thenReturn(52L);
        when(imports.findByBasisQuarterAndIndustryCode("2026Q2", "CS100010"))
                .thenReturn(Optional.of(imported));

        assertThat(service.begin("2026Q2")).contains(52L);

        verify(imported).restart(LocalDateTime.of(2026, 10, 17, 21, 0));
        verify(imports, never()).saveAndFlush(any());
    }
}
