package com.memme.service.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesForecastEntity;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.store.MenuCategory;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.service.sales.analysis.SalesAnalysisResult;
import com.memme.service.sales.analysis.SalesAnalysisService;
import com.memme.service.sales.insight.SalesInsightMetricsAssembler;
import com.memme.service.sales.upload.SalesUploadPersistenceService;
import com.memme.service.solution.SalesSolutionMetricsAssembler;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SalesCategoryFlowTest {

    @Autowired SalesUploadRepository uploads;
    @Autowired SalesOrderRepository orders;
    @Autowired SalesOrderItemRepository items;
    @Autowired SalesUploadPersistenceService persistence;
    @Autowired SalesAnalysisService analysis;
    @Autowired SalesInsightMetricsAssembler insights;
    @Autowired SalesSolutionMetricsAssembler solutions;
    @Autowired EntityManager entityManager;

    @Test
    void 엑셀_파싱부터_DB_조회와_AI_집계까지_6종을_유지하고_취소와_비메뉴를_보존한다() throws Exception {
        long storeId = 598_001L;
        LocalDate date = LocalDate.of(2026, 9, 1);
        TossPosWorkbookData parsed = new TossPosWorkbookParser(new TossPosItemTypeRules(
                Map.of(SalesItemType.DELIVERY_FEE, List.of("배달비"))))
                .parse(new ByteArrayInputStream(workbook()));
        assertThat(parsed.items()).extracting(SalesOrderItem::category).containsExactly(
                MenuCategory.COFFEE, MenuCategory.COFFEE, MenuCategory.TEA, MenuCategory.BEVERAGE,
                MenuCategory.CAKE, MenuCategory.BAKERY, MenuCategory.OTHER, MenuCategory.COFFEE,
                MenuCategory.OTHER, MenuCategory.BEVERAGE, MenuCategory.BEVERAGE);

        SalesUploadEntity upload = SalesUploadEntity.pending(storeId, 7L, "categories.xlsx",
                "sales/category-flow.xlsx", "c".repeat(64));
        upload.startProcessing();
        long uploadId = uploads.saveAndFlush(upload).getId();
        persistence.replaceCoverage(uploadId, storeId, parsed);
        entityManager.flush();
        entityManager.clear();

        var orderIds = orders.findAllBySalesUploadIdOrderByOrderedAtAsc(uploadId).stream()
                .map(order -> order.getId()).toList();
        var saved = items.findAllBySalesOrderIdInOrderBySalesOrderIdAscIdAsc(orderIds);
        assertThat(saved).hasSize(11);
        assertThat(saved).extracting(item -> item.getStandardCategory()).containsAll(List.of(MenuCategory.values()));
        assertThat(saved).extracting(item -> item.getCategoryRaw()).contains("커피", "coffee", "신메뉴");
        assertThat(saved.stream().mapToLong(item -> item.getNetAmount()).sum()).isEqualTo(29_000);
        assertThat(saved.stream().filter(item -> item.getQuantity() < 0).findFirst().orElseThrow().getNetAmount())
                .isEqualTo(-1_000);

        var result = (SalesAnalysisResult.Completed) analysis.analyze(storeId, "CUSTOM", date, date);
        assertThat(result.categories().categories()).extracting(category -> category.categoryName())
                .containsExactlyInAnyOrder("커피", "차", "음료", "케이크", "베이커리", "기타");
        assertThat(result.categories().categories().stream().mapToLong(category -> category.netSales().longValueExact()).sum())
                .isEqualTo(24_000);

        assertThat(result.categories().categories()).filteredOn(category -> category.categoryName().equals("음료"))
                .singleElement().satisfies(category -> assertThat(category.netSales()).isEqualByComparingTo("6000"));

        var insight = insights.assemble(storeId, YearMonth.from(date)).metrics();
        assertThat(insight.categorySales()).extracting(category -> category.categoryName())
                .containsExactlyInAnyOrder("커피", "차", "음료", "케이크", "베이커리", "기타");
        assertThat(insight.categorySales().stream().mapToLong(category -> category.menuSales().longValueExact()).sum())
                .isEqualTo(24_000);

        AnalysisRunEntity run = mock(AnalysisRunEntity.class);
        SalesForecastEntity forecast = mock(SalesForecastEntity.class);
        when(run.getPeriodStart()).thenReturn(date);
        when(run.getPeriodEnd()).thenReturn(date);
        when(forecast.getTargetDate()).thenReturn(date.plusDays(1));
        var solution = solutions.assemble(storeId, run, forecast);
        assertThat(solution.categoryBreakdown()).extracting(category -> category.name())
                .containsExactlyInAnyOrder("커피", "차", "음료", "케이크", "베이커리", "기타");
        assertThat(solution.salesSummary().netSales()).isEqualTo(24_000);
        assertThat(solution.categoryBreakdown()).filteredOn(category -> category.name().equals("커피"))
                .singleElement().satisfies(category -> assertThat(category.share()).isEqualTo(0.0417));
    }

    private byte[] workbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            Sheet basis = workbook.createSheet("데이터 기준");
            write(basis.createRow(0), "시작일자", "종료일자", "매출 정산 기준", "매출 시작 시간");
            write(basis.createRow(2), "2026-09-01", "2026-09-01", "주문한 날", "00:00:00");
            Sheet summary = workbook.createSheet("상품 주문 합계");
            write(summary.createRow(0), "기간", "실 판매 금액 (할인, 옵션 포함)");
            write(summary.createRow(1), "2026-09-01", 29_000);
            Sheet detail = workbook.createSheet("상품 주문 상세내역");
            write(detail.createRow(0), "주문기준일자", "결제상태", "주문시작시각", "주문채널", "주문번호",
                    "상품명", "카테고리", "옵션", "상품할인", "주문할인", "수량", "상품가격", "옵션가격",
                    "상품할인 금액", "주문할인 금액", "실판매금액 (할인, 옵션 포함)", "과세여부", "부가세액");
            String[] names = {"아메리카노", "에스프레소", "얼그레이", "레몬에이드", "치즈 케이크",
                    "스콘", "김밥", "아메리카노", "배달비", "초코 라떼", "딸기 스무디"};
            String[] categories = {"커피", "coffee", "차", "음료", "디저트", "신메뉴", "food", "커피", "", "non", "smoothie"};
            int[] amounts = {1_000, 1_000, 2_000, 3_000, 4_000, 5_000, 6_000, -1_000, 5_000, 2_000, 1_000};
            for (int i = 0; i < names.length; i++) {
                int amount = amounts[i];
                write(detail.createRow(i + 2), "2026-09-01", amount < 0 ? "취소" : "완료",
                        "2026-09-01 10:00:00", "포스", (i == 0 || i == 1 || i == 7) ? "category-coffee" : "category-" + i, names[i], categories[i], "", "", "",
                        amount < 0 ? -1 : 1, amount, 0, 0, 0, amount, "면세", 0);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void write(Row row, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof Number number) row.createCell(i).setCellValue(number.doubleValue());
            else row.createCell(i).setCellValue(values[i].toString());
        }
    }
}
