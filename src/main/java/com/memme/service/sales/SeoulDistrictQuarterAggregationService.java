package com.memme.service.sales;

import com.memme.dto.solution.external.SeoulEstimatedSalesResponse;
import com.memme.dto.solution.external.SeoulFootTrafficResponse;
import com.memme.dto.solution.external.SeoulStoreCountResponse;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.springframework.stereotype.Service;

@Service
public class SeoulDistrictQuarterAggregationService {

    private static final String INDUSTRY_CODE = "CS100010";
    private static final String[] BANDS = {"00-06", "06-11", "11-14", "14-17", "17-21", "21-24"};
    private static final int[] BAND_HOURS = {6, 5, 3, 3, 4, 3};

    public List<Benchmark> aggregate(String basisQuarter,
                                     List<SeoulEstimatedSalesResponse.Row> salesRows,
                                     List<SeoulStoreCountResponse.Row> storeRows,
                                     List<SeoulFootTrafficResponse.Row> trafficRows) {
        if (basisQuarter == null || !basisQuarter.matches("[0-9]{4}Q[1-4]")) {
            throw new IllegalArgumentException("basisQuarter must be YYYYQ1..YYYYQ4");
        }
        String sourceQuarter = basisQuarter.replace("Q", "");
        Map<String, SeoulStoreCountResponse.Row> stores = new HashMap<>();
        Set<String> storeDistricts = new HashSet<>();
        for (var row : storeRows) {
            String dongCode = row == null ? null : normalizeDong(row.administrativeDongCode());
            if (row != null && sourceQuarter.equals(row.quarterCode())
                    && INDUSTRY_CODE.equals(row.industryCode()) && dongCode != null) {
                if (!storeDistricts.add(dongCode)) {
                    throw new IllegalStateException("Duplicate Seoul store-count district");
                }
                if (row.storeCount() != null && row.storeCount() >= 3 && row.storeCount() <= Integer.MAX_VALUE) {
                    stores.put(dongCode, row);
                }
            }
        }
        Map<String, SeoulFootTrafficResponse.Row> traffic = new HashMap<>();
        for (var row : trafficRows) {
            String dongCode = row == null ? null : normalizeDong(row.administrativeDongCode());
            if (row != null && sourceQuarter.equals(row.quarterCode()) && dongCode != null) {
                if (traffic.putIfAbsent(dongCode, row) != null) {
                    throw new IllegalStateException("Duplicate Seoul foot-traffic district");
                }
            }
        }
        List<Benchmark> result = new ArrayList<>();
        Set<String> salesDistricts = new HashSet<>();
        for (var row : salesRows) {
            String dongCode = row == null ? null : normalizeDong(row.administrativeDongCode());
            if (row == null || !sourceQuarter.equals(row.quarterCode())
                    || !INDUSTRY_CODE.equals(row.industryCode()) || dongCode == null) {
                continue;
            }
            if (!salesDistricts.add(dongCode)) {
                throw new IllegalStateException("Duplicate Seoul sales district");
            }
            var store = stores.get(dongCode);
            var people = traffic.get(dongCode);
            if (store == null || people == null) {
                continue;
            }
            if (row.salesAmount() == null || row.salesAmount() < 0) {
                throw new IllegalStateException("Invalid Seoul sales amount");
            }
            String salesBand = peakBand(new Long[] {row.salesFrom00To06(), row.salesFrom06To11(),
                    row.salesFrom11To14(), row.salesFrom14To17(), row.salesFrom17To21(), row.salesFrom21To24()});
            String trafficBand = peakBand(new Long[] {people.from00To06(), people.from06To11(),
                    people.from11To14(), people.from14To17(), people.from17To21(), people.from21To24()});
            result.add(new Benchmark(dongCode, store.storeCount().intValue(),
                    row.salesAmount() / store.storeCount(), salesBand, trafficBand));
        }
        return List.copyOf(result);
    }

    private static String normalizeDong(String code) {
        if (code != null && code.matches("[0-9]{8}")) {
            return code + "00";
        }
        return code != null && code.matches("[0-9]{10}") ? code : null;
    }

    private static String peakBand(Long[] values) {
        int best = 0;
        for (int index = 0; index < values.length; index++) {
            if (values[index] == null || values[index] < 0) {
                throw new IllegalStateException("Invalid Seoul hourly metric");
            }
            if (BigInteger.valueOf(values[index]).multiply(BigInteger.valueOf(BAND_HOURS[best]))
                    .compareTo(BigInteger.valueOf(values[best]).multiply(BigInteger.valueOf(BAND_HOURS[index]))) > 0) {
                best = index;
            }
        }
        return BANDS[best];
    }

    public record Benchmark(String administrativeDongCode, int cafeStoreCount,
                            long districtAvgMonthlySalesPerCafe, String peakSalesHourBand,
                            String peakFootTrafficHourBand) {}
}
