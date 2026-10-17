package com.memme.service.sales;

import com.memme.entity.sales.SeoulDistrictQuarterBenchmarkEntity;
import com.memme.entity.sales.SeoulDistrictQuarterImportEntity;
import com.memme.entity.sales.SeoulDistrictQuarterImportStatus;
import com.memme.repository.sales.SeoulDistrictQuarterBenchmarkRepository;
import com.memme.repository.sales.SeoulDistrictQuarterImportRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeoulDistrictQuarterImportPersistenceService {

    private static final String INDUSTRY_CODE = "CS100010";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final SeoulDistrictQuarterImportRepository imports;
    private final SeoulDistrictQuarterBenchmarkRepository benchmarks;
    private final Clock clock;

    @Autowired
    public SeoulDistrictQuarterImportPersistenceService(SeoulDistrictQuarterImportRepository imports,
                                                        SeoulDistrictQuarterBenchmarkRepository benchmarks) {
        this(imports, benchmarks, Clock.system(KOREA));
    }

    SeoulDistrictQuarterImportPersistenceService(SeoulDistrictQuarterImportRepository imports,
                                                 SeoulDistrictQuarterBenchmarkRepository benchmarks, Clock clock) {
        this.imports = imports;
        this.benchmarks = benchmarks;
        this.clock = clock;
    }

    @Transactional
    public Optional<Long> begin(String basisQuarter) {
        var existing = imports.findByBasisQuarterAndIndustryCode(basisQuarter, INDUSTRY_CODE);
        if (existing.isPresent()) {
            var imported = existing.get();
            if (imported.getStatus() != SeoulDistrictQuarterImportStatus.FAILED) {
                return Optional.empty();
            }
            imported.restart(now());
            return Optional.of(imported.getId());
        }
        var created = imports.saveAndFlush(SeoulDistrictQuarterImportEntity.start(
                basisQuarter, INDUSTRY_CODE, now()));
        return Optional.of(created.getId());
    }

    @Transactional
    public void complete(long importId, List<SeoulDistrictQuarterAggregationService.Benchmark> rows) {
        var imported = imports.findById(importId).orElseThrow();
        if (imported.getStatus() != SeoulDistrictQuarterImportStatus.PROCESSING) {
            throw new IllegalStateException("Quarter import is not processing");
        }
        for (var row : rows) {
            benchmarks.save(SeoulDistrictQuarterBenchmarkEntity.create(imported,
                    row.administrativeDongCode(), row.cafeStoreCount(), row.districtAvgMonthlySalesPerCafe(),
                    row.peakSalesHourBand(), row.peakFootTrafficHourBand()));
        }
        imported.complete(now());
    }

    @Transactional
    public void fail(long importId) {
        var imported = imports.findById(importId).orElseThrow();
        if (imported.getStatus() == SeoulDistrictQuarterImportStatus.PROCESSING) {
            imported.fail(now());
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(KOREA));
    }
}
