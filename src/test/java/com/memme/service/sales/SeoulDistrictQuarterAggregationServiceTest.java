package com.memme.service.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.dto.solution.external.SeoulEstimatedSalesResponse;
import com.memme.dto.solution.external.SeoulFootTrafficResponse;
import com.memme.dto.solution.external.SeoulStoreCountResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeoulDistrictQuarterAggregationServiceTest {

    private final SeoulDistrictQuarterAggregationService service = new SeoulDistrictQuarterAggregationService();

    @Test
    void 서울_API의_8자리_행정동_코드를_매장_조회용_10자리로_적재한다() {
        var sales = new SeoulEstimatedSalesResponse.Row("20262", "11680510", "역삼1동",
                "CS100010", "커피-음료", 12_000_000L, 100L,
                60L, 50L, 30L, 90L, 120L, 30L);
        var stores = new SeoulStoreCountResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 4L, 0L, 0L);
        var traffic = new SeoulFootTrafficResponse.Row("20262", "11680510", "역삼1동",
                1000L, 60L, 50L, 30L, 90L, 120L, 30L);

        assertThat(service.aggregate("2026Q2", List.of(sales), List.of(stores), List.of(traffic)))
                .singleElement()
                .extracting(SeoulDistrictQuarterAggregationService.Benchmark::administrativeDongCode)
                .isEqualTo("1168051000");
    }

    @Test
    void 같은_행정동의_8자리와_10자리_행은_중복으로_판단한다() {
        var stores = List.of(
                new SeoulStoreCountResponse.Row("20262", "11680510", "역삼1동",
                        "CS100010", "커피-음료", 4L, 0L, 0L),
                new SeoulStoreCountResponse.Row("20262", "1168051000", "역삼1동",
                        "CS100010", "커피-음료", 4L, 0L, 0L));

        assertThatThrownBy(() -> service.aggregate("2026Q2", List.of(), stores, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate Seoul store-count district");
    }

    @Test
    void 숫자가_아닌_서울_API_행정동_코드는_적재하지_않는다() {
        var sales = new SeoulEstimatedSalesResponse.Row("20262", "1168051X", "역삼1동",
                "CS100010", "커피-음료", 12_000_000L, 100L,
                60L, 50L, 30L, 90L, 120L, 30L);
        var stores = new SeoulStoreCountResponse.Row("20262", "11680510", "역삼1동",
                "CS100010", "커피-음료", 4L, 0L, 0L);
        var traffic = new SeoulFootTrafficResponse.Row("20262", "11680510", "역삼1동",
                1000L, 60L, 50L, 30L, 90L, 120L, 30L);

        assertThat(service.aggregate("2026Q2", List.of(sales), List.of(stores), List.of(traffic)))
                .isEmpty();
    }

    @Test
    void 같은_분기_행정동의_세_자료로_점포당_월평균과_시간당_피크를_계산한다() {
        var sales = new SeoulEstimatedSalesResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 12_000_000L, 100L,
                60L, 50L, 30L, 90L, 120L, 30L);
        var stores = new SeoulStoreCountResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 4L, 0L, 0L);
        var traffic = new SeoulFootTrafficResponse.Row("20262", "1168051000", "역삼1동",
                1000L, 60L, 50L, 30L, 90L, 120L, 30L);

        var result = service.aggregate("2026Q2", List.of(sales), List.of(stores), List.of(traffic));

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.administrativeDongCode()).isEqualTo("1168051000");
            assertThat(item.cafeStoreCount()).isEqualTo(4);
            assertThat(item.districtAvgMonthlySalesPerCafe()).isEqualTo(3_000_000L);
            assertThat(item.peakSalesHourBand()).isEqualTo("14-17");
            assertThat(item.peakFootTrafficHourBand()).isEqualTo("14-17");
        });
    }

    @Test
    void 다른_분기의_점포자료는_요약하지_않는다() {
        var sales = new SeoulEstimatedSalesResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 12_000_000L, 100L,
                60L, 50L, 30L, 90L, 120L, 30L);
        var wrongQuarterStores = new SeoulStoreCountResponse.Row("20261", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 4L, 0L, 0L);
        var traffic = new SeoulFootTrafficResponse.Row("20262", "1168051000", "역삼1동",
                1000L, 60L, 50L, 30L, 90L, 120L, 30L);

        assertThat(service.aggregate("2026Q2", List.of(sales), List.of(wrongQuarterStores), List.of(traffic)))
                .isEmpty();
    }

    @Test
    void 점포수가_세곳_미만이면_요약하지_않는다() {
        var sales = new SeoulEstimatedSalesResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 12_000_000L, 100L,
                60L, 50L, 30L, 90L, 120L, 30L);
        var stores = new SeoulStoreCountResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 2L, 0L, 0L);
        var traffic = new SeoulFootTrafficResponse.Row("20262", "1168051000", "역삼1동",
                1000L, 60L, 50L, 30L, 90L, 120L, 30L);

        assertThat(service.aggregate("2026Q2", List.of(sales), List.of(stores), List.of(traffic)))
                .isEmpty();
    }

    @Test
    void 점포_수가_기준_미달인_중복행이_있어도_중복자료로_실패한다() {
        var sales = new SeoulEstimatedSalesResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 12_000_000L, 100L,
                60L, 50L, 30L, 90L, 120L, 30L);
        var eligibleStores = new SeoulStoreCountResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 4L, 0L, 0L);
        var ineligibleDuplicate = new SeoulStoreCountResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 2L, 0L, 0L);
        var traffic = new SeoulFootTrafficResponse.Row("20262", "1168051000", "역삼1동",
                1000L, 60L, 50L, 30L, 90L, 120L, 30L);

        assertThatThrownBy(() -> service.aggregate("2026Q2", List.of(sales),
                List.of(eligibleStores, ineligibleDuplicate), List.of(traffic)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 비교_대상_행정동의_필수_매출_시간대_값이_없으면_실패한다() {
        var sales = new SeoulEstimatedSalesResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 12_000_000L, 100L,
                60L, 50L, 30L, null, 120L, 30L);
        var stores = new SeoulStoreCountResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 4L, 0L, 0L);
        var traffic = new SeoulFootTrafficResponse.Row("20262", "1168051000", "역삼1동",
                1000L, 60L, 50L, 30L, 90L, 120L, 30L);

        assertThatThrownBy(() -> service.aggregate("2026Q2", List.of(sales), List.of(stores), List.of(traffic)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 세_자료가_모두_있는_행정동만_벤치마크에_포함한다() {
        var completeSales = new SeoulEstimatedSalesResponse.Row("20262", "1168051000", "역삼1동",
                "CS100010", "커피-음료", 12_000_000L, 100L,
                60L, 50L, 30L, 90L, 120L, 30L);
        var salesWithoutTraffic = new SeoulEstimatedSalesResponse.Row("20262", "1168052000", "역삼2동",
                "CS100010", "커피-음료", 9_000_000L, 80L,
                60L, 50L, 30L, 90L, 120L, 30L);
        var stores = List.of(
                new SeoulStoreCountResponse.Row("20262", "1168051000", "역삼1동",
                        "CS100010", "커피-음료", 4L, 0L, 0L),
                new SeoulStoreCountResponse.Row("20262", "1168052000", "역삼2동",
                        "CS100010", "커피-음료", 4L, 0L, 0L));
        var traffic = new SeoulFootTrafficResponse.Row("20262", "1168051000", "역삼1동",
                1000L, 60L, 50L, 30L, 90L, 120L, 30L);

        assertThat(service.aggregate("2026Q2", List.of(completeSales, salesWithoutTraffic), stores,
                List.of(traffic))).extracting(SeoulDistrictQuarterAggregationService.Benchmark::administrativeDongCode)
                .containsExactly("1168051000");
    }
}
