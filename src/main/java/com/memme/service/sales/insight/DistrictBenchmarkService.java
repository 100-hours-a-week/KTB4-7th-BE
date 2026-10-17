package com.memme.service.sales.insight;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Set;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.memme.dto.sales.DistrictBenchmarkMetric;
import com.memme.entity.sales.SeoulDistrictQuarterBenchmarkEntity;
import com.memme.entity.sales.SeoulDistrictQuarterImportEntity;
import com.memme.entity.sales.SeoulDistrictQuarterImportStatus;
import com.memme.entity.store.Store;
import com.memme.repository.sales.SeoulDistrictQuarterBenchmarkRepository;
import com.memme.repository.sales.SeoulDistrictQuarterImportRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.store.AdministrativeDistrict;
import com.memme.service.store.StoreLocationService;
import org.springframework.stereotype.Service;

@Service
public class DistrictBenchmarkService {

    private static final String CAFE_INDUSTRY_CODE = "CS100010";
    private static final Pattern QUARTER_PATTERN = Pattern.compile("^([1-9]\\d{3})Q([1-4])$");
    private static final Pattern EIGHT_DIGIT_DONG_CODE = Pattern.compile("^\\d{8}$");
    private static final Pattern TEN_DIGIT_DONG_CODE = Pattern.compile("^\\d{10}$");
    private static final Set<String> VALID_HOUR_BANDS = Set.of(
            "00-06", "06-11", "11-14", "14-17", "17-21", "21-24");
    private static final int MAX_QUARTER_AGE = 2;

    private final StoreRepository storeRepository;
    private final StoreLocationService storeLocationService;
    private final SeoulDistrictQuarterImportRepository importRepository;
    private final SeoulDistrictQuarterBenchmarkRepository benchmarkRepository;

    public DistrictBenchmarkService(
            StoreRepository storeRepository,
            StoreLocationService storeLocationService,
            SeoulDistrictQuarterImportRepository importRepository,
            SeoulDistrictQuarterBenchmarkRepository benchmarkRepository
    ) {
        this.storeRepository = storeRepository;
        this.storeLocationService = storeLocationService;
        this.importRepository = importRepository;
        this.benchmarkRepository = benchmarkRepository;
    }

    public Optional<DistrictBenchmarkMetric> findForInsight(long storeId, YearMonth targetMonth) {
        Optional<Store> optionalStore = storeRepository.findById(storeId);
        if (optionalStore.isEmpty()) {
            return Optional.empty();
        }

        Store store = optionalStore.get();
        String dongCode = resolveAdministrativeDongCode(store);
        if (dongCode == null) {
            return Optional.empty();
        }

        String latestQuarter = quarterOf(targetMonth);
        long latestQuarterIndex = quarterIndex(latestQuarter);
        long earliestAllowedQuarterIndex = latestQuarterIndex - MAX_QUARTER_AGE;
        return importRepository
                .findByIndustryCodeAndStatusAndBasisQuarterLessThanEqualOrderByBasisQuarterDesc(
                        CAFE_INDUSTRY_CODE,
                        SeoulDistrictQuarterImportStatus.COMPLETED,
                        latestQuarter
                ).stream()
                .filter(imported -> isWithinFreshnessWindow(imported, earliestAllowedQuarterIndex, latestQuarterIndex))
                .map(imported -> toMetric(imported, dongCode))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private String resolveAdministrativeDongCode(Store store) {
        String existingCode = normalizeAdministrativeDongCode(store.getAdministrativeDongCode());
        if (existingCode != null) {
            return existingCode;
        }

        BigDecimal latitude = store.getLatitude();
        BigDecimal longitude = store.getLongitude();
        if (latitude == null || longitude == null) {
            return null;
        }

        Optional<AdministrativeDistrict> district = storeLocationService
                .findAdministrativeDistrict(longitude, latitude);
        if (district.isEmpty()) {
            return null;
        }
        String resolvedCode = normalizeAdministrativeDongCode(district.get().code());
        if (resolvedCode == null) {
            return null;
        }

        store.updateAdministrativeDongCode(resolvedCode);
        storeRepository.save(store);
        return resolvedCode;
    }

    private String normalizeAdministrativeDongCode(String rawCode) {
        if (rawCode == null) {
            return null;
        }
        String code = rawCode.trim();
        if (TEN_DIGIT_DONG_CODE.matcher(code).matches()) {
            return code;
        }
        if (EIGHT_DIGIT_DONG_CODE.matcher(code).matches()) {
            return code + "00";
        }
        return null;
    }

    private boolean isWithinFreshnessWindow(
            SeoulDistrictQuarterImportEntity imported,
            long earliestAllowedQuarterIndex,
            long latestQuarterIndex
    ) {
        long quarterIndex = quarterIndex(imported.getBasisQuarter());
        return quarterIndex >= earliestAllowedQuarterIndex && quarterIndex <= latestQuarterIndex;
    }

    private Optional<DistrictBenchmarkMetric> toMetric(
            SeoulDistrictQuarterImportEntity imported,
            String dongCode
    ) {
        if (imported.getId() == null) {
            return Optional.empty();
        }
        String basisQuarter = normalize(imported.getBasisQuarter());
        if (!isValidQuarter(basisQuarter)) {
            return Optional.empty();
        }
        Optional<SeoulDistrictQuarterBenchmarkEntity> benchmark = benchmarkRepository
                .findByImportedIdAndAdministrativeDongCode(imported.getId(), dongCode);
        if (benchmark.isEmpty() || benchmark.get().getCafeStoreCount() < 3) {
            return Optional.empty();
        }
        try {
            SeoulDistrictQuarterBenchmarkEntity value = benchmark.get();
            String peakSalesHourBand = normalize(value.getPeakSalesHourBand());
            String peakFootTrafficHourBand = normalize(value.getPeakFootTrafficHourBand());
            long monthlySales = value.getDistrictAvgMonthlySalesPerCafe();
            if (monthlySales < 0
                    || !isValidHourBand(peakSalesHourBand)
                    || !isValidHourBand(peakFootTrafficHourBand)) {
                return Optional.empty();
            }
            return Optional.of(new DistrictBenchmarkMetric(
                    basisQuarter,
                    Math.toIntExact(monthlySales),
                    value.getCafeStoreCount(),
                    peakSalesHourBand,
                    peakFootTrafficHourBand
            ));
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String quarterOf(YearMonth month) {
        return "%04dQ%d".formatted(month.getYear(), (month.getMonthValue() - 1) / 3 + 1);
    }

    private long quarterIndex(String quarter) {
        Matcher matcher = QUARTER_PATTERN.matcher(quarter == null ? "" : quarter.trim());
        if (!matcher.matches()) {
            return Long.MIN_VALUE;
        }
        return Long.parseLong(matcher.group(1)) * 4 + Integer.parseInt(matcher.group(2)) - 1;
    }

    private boolean isValidQuarter(String quarter) {
        return quarter != null && QUARTER_PATTERN.matcher(quarter).matches();
    }

    private boolean isValidHourBand(String hourBand) {
        return hourBand != null && VALID_HOUR_BANDS.contains(hourBand);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
