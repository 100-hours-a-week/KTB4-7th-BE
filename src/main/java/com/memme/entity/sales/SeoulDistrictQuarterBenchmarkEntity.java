package com.memme.entity.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "seoul_district_quarter_benchmarks", uniqueConstraints = @UniqueConstraint(
        name = "uk_seoul_district_benchmark_import_dong", columnNames = {"import_id", "administrative_dong_code"}))
public class SeoulDistrictQuarterBenchmarkEntity {

    private static final Set<String> BANDS = Set.of("00-06", "06-11", "11-14", "14-17", "17-21", "21-24");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_id", nullable = false)
    private SeoulDistrictQuarterImportEntity imported;

    @Column(name = "administrative_dong_code", nullable = false, length = 10, columnDefinition = "CHAR(10)")
    private String administrativeDongCode;

    @Column(name = "cafe_store_count", nullable = false)
    private int cafeStoreCount;

    @Column(name = "district_avg_monthly_sales_per_cafe", nullable = false)
    private long districtAvgMonthlySalesPerCafe;

    @Column(name = "peak_sales_hour_band", nullable = false, length = 5, columnDefinition = "CHAR(5)")
    private String peakSalesHourBand;

    @Column(name = "peak_foot_traffic_hour_band", nullable = false, length = 5, columnDefinition = "CHAR(5)")
    private String peakFootTrafficHourBand;

    protected SeoulDistrictQuarterBenchmarkEntity() {}

    public static SeoulDistrictQuarterBenchmarkEntity create(
            SeoulDistrictQuarterImportEntity imported, String administrativeDongCode,
            int cafeStoreCount, long districtAvgMonthlySalesPerCafe,
            String peakSalesHourBand, String peakFootTrafficHourBand) {
        Objects.requireNonNull(imported, "imported");
        if (administrativeDongCode == null || !administrativeDongCode.matches("[0-9]{10}")) {
            throw new IllegalArgumentException("administrativeDongCode must be ten digits");
        }
        if (cafeStoreCount < 3 || districtAvgMonthlySalesPerCafe < 0) {
            throw new IllegalArgumentException("invalid benchmark amounts");
        }
        if (!BANDS.contains(peakSalesHourBand) || !BANDS.contains(peakFootTrafficHourBand)) {
            throw new IllegalArgumentException("invalid peak hour band");
        }
        SeoulDistrictQuarterBenchmarkEntity benchmark = new SeoulDistrictQuarterBenchmarkEntity();
        benchmark.imported = imported;
        benchmark.administrativeDongCode = administrativeDongCode;
        benchmark.cafeStoreCount = cafeStoreCount;
        benchmark.districtAvgMonthlySalesPerCafe = districtAvgMonthlySalesPerCafe;
        benchmark.peakSalesHourBand = peakSalesHourBand;
        benchmark.peakFootTrafficHourBand = peakFootTrafficHourBand;
        return benchmark;
    }

    public Long getId() { return id; }
    public SeoulDistrictQuarterImportEntity getImported() { return imported; }
    public String getAdministrativeDongCode() { return administrativeDongCode; }
    public int getCafeStoreCount() { return cafeStoreCount; }
    public long getDistrictAvgMonthlySalesPerCafe() { return districtAvgMonthlySalesPerCafe; }
    public String getPeakSalesHourBand() { return peakSalesHourBand; }
    public String getPeakFootTrafficHourBand() { return peakFootTrafficHourBand; }
}
