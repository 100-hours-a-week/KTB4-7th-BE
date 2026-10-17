package com.memme.service.sales.insight;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.memme.dto.sales.DistrictBenchmarkMetric;
import com.memme.entity.auth.User;
import com.memme.entity.sales.SeoulDistrictQuarterBenchmarkEntity;
import com.memme.entity.sales.SeoulDistrictQuarterImportEntity;
import com.memme.entity.sales.SeoulDistrictQuarterImportStatus;
import com.memme.entity.store.Store;
import com.memme.repository.sales.SeoulDistrictQuarterBenchmarkRepository;
import com.memme.repository.sales.SeoulDistrictQuarterImportRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.store.AdministrativeDistrict;
import com.memme.service.store.StoreLocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistrictBenchmarkServiceTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final String DONG_CODE = "1168064000";

    private StoreRepository storeRepository;
    private StoreLocationService locationService;
    private SeoulDistrictQuarterImportRepository importRepository;
    private SeoulDistrictQuarterBenchmarkRepository benchmarkRepository;
    private DistrictBenchmarkService service;

    @BeforeEach
    void setUp() {
        storeRepository = mock(StoreRepository.class);
        locationService = mock(StoreLocationService.class);
        importRepository = mock(SeoulDistrictQuarterImportRepository.class);
        benchmarkRepository = mock(SeoulDistrictQuarterBenchmarkRepository.class);
        service = new DistrictBenchmarkService(
                storeRepository, locationService, importRepository, benchmarkRepository);
    }

    @Test
    void usesTheNewestAvailableCompletedBenchmarkWithinTwoQuarters() {
        Store store = storeWithCode(DONG_CODE);
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        List<SeoulDistrictQuarterImportEntity> imports = List.of(
                imported("2026Q4", 4L), imported("2026Q3", 3L),
                imported("2026Q2", 2L), imported("2026Q1", 1L));
        when(importRepository.findByIndustryCodeAndStatusAndBasisQuarterLessThanEqualOrderByBasisQuarterDesc(
                "CS100010", SeoulDistrictQuarterImportStatus.COMPLETED, "2026Q4"))
                .thenReturn(imports);
        SeoulDistrictQuarterBenchmarkEntity benchmark = benchmark(imports.get(2));
        when(benchmarkRepository.findByImportedIdAndAdministrativeDongCode(4L, DONG_CODE))
                .thenReturn(Optional.empty());
        when(benchmarkRepository.findByImportedIdAndAdministrativeDongCode(3L, DONG_CODE))
                .thenReturn(Optional.empty());
        when(benchmarkRepository.findByImportedIdAndAdministrativeDongCode(2L, DONG_CODE))
                .thenReturn(Optional.of(benchmark));

        Optional<DistrictBenchmarkMetric> result = service.findForInsight(1L, YearMonth.of(2026, 10));

        assertThat(result).contains(new DistrictBenchmarkMetric(
                "2026Q2", 3_000_000, 12, "14-17", "17-21"));
        verify(benchmarkRepository, never()).findByImportedIdAndAdministrativeDongCode(1L, DONG_CODE);
        verify(storeRepository, never()).save(any());
        verify(locationService, never()).findAdministrativeDistrict(any(), any());
    }

    @Test
    void skipsMalformedStoredBenchmarkAndFallsBackToOlderValidQuarter() {
        Store store = storeWithCode(DONG_CODE);
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        List<SeoulDistrictQuarterImportEntity> imports = List.of(
                imported("2026Q4", 4L), imported("2026Q3", 3L));
        when(importRepository.findByIndustryCodeAndStatusAndBasisQuarterLessThanEqualOrderByBasisQuarterDesc(
                "CS100010", SeoulDistrictQuarterImportStatus.COMPLETED, "2026Q4"))
                .thenReturn(imports);
        SeoulDistrictQuarterBenchmarkEntity malformed = benchmark(imports.get(0));
        when(malformed.getPeakSalesHourBand()).thenReturn("15-18");
        SeoulDistrictQuarterBenchmarkEntity olderValid = benchmark(imports.get(1));
        when(benchmarkRepository.findByImportedIdAndAdministrativeDongCode(4L, DONG_CODE))
                .thenReturn(Optional.of(malformed));
        when(benchmarkRepository.findByImportedIdAndAdministrativeDongCode(3L, DONG_CODE))
                .thenReturn(Optional.of(olderValid));

        Optional<DistrictBenchmarkMetric> result = service.findForInsight(1L, YearMonth.of(2026, 10));

        assertThat(result).contains(new DistrictBenchmarkMetric(
                "2026Q3", 3_000_000, 12, "14-17", "17-21"));
    }

    @Test
    void backfillsMissingCodeFromSavedCoordinatesAndPersistsTheTenDigitCode() {
        Store store = storeWithCoordinates();
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(locationService.findAdministrativeDistrict(
                new BigDecimal("126.8646"), new BigDecimal("37.4786")))
                .thenReturn(Optional.of(new AdministrativeDistrict("11680640", "서울특별시 강남구 역삼1동")));
        when(importRepository.findByIndustryCodeAndStatusAndBasisQuarterLessThanEqualOrderByBasisQuarterDesc(
                "CS100010", SeoulDistrictQuarterImportStatus.COMPLETED, "2026Q3"))
                .thenReturn(List.of());

        assertThat(service.findForInsight(1L, YearMonth.of(2026, 7))).isEmpty();

        assertThat(store.getAdministrativeDongCode()).isEqualTo(DONG_CODE);
        verify(storeRepository).save(store);
        verify(benchmarkRepository, never()).findByImportedIdAndAdministrativeDongCode(any(), any());
    }

    @Test
    void doesNotResolveAddressOrCallExternalLocationServiceWhenCoordinatesAreMissing() {
        Store store = storeWithNoLocation();
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));

        assertThat(service.findForInsight(1L, YearMonth.of(2026, 7))).isEmpty();

        verify(locationService, never()).findAdministrativeDistrict(any(), any());
        verify(importRepository, never())
                .findByIndustryCodeAndStatusAndBasisQuarterLessThanEqualOrderByBasisQuarterDesc(any(), any(), any());
    }

    @Test
    void omitsBenchmarkWhenNoCompletedUsableDistrictDataExists() {
        Store store = storeWithCode(DONG_CODE);
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        SeoulDistrictQuarterImportEntity oldImport = imported("2026Q1", 1L);
        when(importRepository.findByIndustryCodeAndStatusAndBasisQuarterLessThanEqualOrderByBasisQuarterDesc(
                "CS100010", SeoulDistrictQuarterImportStatus.COMPLETED, "2026Q4"))
                .thenReturn(List.of(oldImport));

        assertThat(service.findForInsight(1L, YearMonth.of(2026, 10))).isEmpty();

        verify(benchmarkRepository, never()).findByImportedIdAndAdministrativeDongCode(1L, DONG_CODE);
    }

    private Store storeWithCode(String code) {
        Store store = storeWithNoLocation();
        store.updateAdministrativeDongCode(code);
        return store;
    }

    private Store storeWithCoordinates() {
        Store store = storeWithNoLocation();
        store.updateCoordinates(new BigDecimal("37.4786"), new BigDecimal("126.8646"));
        return store;
    }

    private Store storeWithNoLocation() {
        User owner = User.create("owner@example.com", "hash", "01012345678", CREATED_AT);
        return Store.create(owner, null, null, "매장", "12345", "서울시 강남구", null, CREATED_AT);
    }

    private SeoulDistrictQuarterImportEntity imported(String quarter, Long id) {
        SeoulDistrictQuarterImportEntity imported = mock(SeoulDistrictQuarterImportEntity.class);
        when(imported.getId()).thenReturn(id);
        when(imported.getBasisQuarter()).thenReturn(quarter);
        return imported;
    }

    private SeoulDistrictQuarterBenchmarkEntity benchmark(SeoulDistrictQuarterImportEntity imported) {
        SeoulDistrictQuarterBenchmarkEntity benchmark = mock(SeoulDistrictQuarterBenchmarkEntity.class);
        when(benchmark.getImported()).thenReturn(imported);
        when(benchmark.getCafeStoreCount()).thenReturn(12);
        when(benchmark.getDistrictAvgMonthlySalesPerCafe()).thenReturn(3_000_000L);
        when(benchmark.getPeakSalesHourBand()).thenReturn("14-17");
        when(benchmark.getPeakFootTrafficHourBand()).thenReturn("17-21");
        return benchmark;
    }
}
