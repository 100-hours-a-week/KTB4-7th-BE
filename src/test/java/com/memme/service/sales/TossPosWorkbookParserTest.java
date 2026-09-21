package com.memme.service.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.exception.TossPosWorkbookValidationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class TossPosWorkbookParserTest {

    private final TossPosWorkbookParser parser = new TossPosWorkbookParser(itemTypeRules());

    @Test
    void 합성_정상_파일을_파싱하면_표준_주문과_항목과_일별_집계를_생성한다() throws Exception {
        try (InputStream inputStream = getClass()
            .getResourceAsStream("/sales/toss-pos/sample-normal.xlsx")) {

            TossPosWorkbookData result = parser.parse(inputStream);

            assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 1, 2));
            assertThat(result.items()).hasSize(5);
            assertThat(result.orders()).hasSize(4);
            assertThat(result.dailySummaries()).hasSize(2);
            assertThat(result.dailySummaries())
                .extracting(DailySalesSummary::totalNetAmount)
                .satisfies(amounts -> assertThat(amounts.stream().mapToLong(Long::longValue).sum())
                    .isEqualTo(5_200L));
        }
    }

    @Test
    void 취소_행을_삭제하지_않고_음수_수량과_금액으로_보존한다() throws Exception {
        TossPosWorkbookData result = parseSynthetic(false, 0);

        assertThat(result.items())
            .filteredOn(item -> item.status() == SalesOrderStatus.CANCELED)
            .singleElement()
            .satisfies(item -> {
                assertThat(item.quantity()).isEqualTo(-1);
                assertThat(item.netAmount()).isEqualTo(-1_000L);
            });
    }

    @Test
    void 같은_주문번호라도_채널이나_주문시각이_다르면_별도_주문이다() throws Exception {
        TossPosWorkbookData result = parseSynthetic(false, 0);

        assertThat(result.orders())
            .filteredOn(order -> order.key().posOrderNo().equals("000123"))
            .hasSize(3);
    }

    @Test
    void 전체_취소_주문은_유효_주문에서_제외한다() throws Exception {
        TossPosWorkbookData result = parseSynthetic(false, 0);

        assertThat(result.orders())
            .filteredOn(order -> order.key().orderedAt().getHour() == 10)
            .singleElement()
            .extracting(SalesOrder::valid)
            .isEqualTo(false);
    }

    @Test
    void 비메뉴만_있는_주문은_유효_주문에서_제외한다() throws Exception {
        TossPosWorkbookData result = parseSynthetic(false, 0);

        assertThat(result.orders())
            .filteredOn(order -> order.key().channel() == SalesChannel.DELIVERY)
            .singleElement()
            .satisfies(order -> {
                assertThat(order.valid()).isFalse();
                assertThat(order.items()).extracting(SalesOrderItem::itemType)
                    .containsOnly(SalesItemType.DELIVERY_FEE);
            });
    }

    @Test
    void 이벤트라는_단어가_있는_0원_메뉴도_메뉴_수량과_유효_주문에_포함한다() throws Exception {
        TossPosWorkbookData result = parseSynthetic(false, 0);

        assertThat(result.items())
            .filteredOn(item -> item.menuName().equals("한입김밥 이벤트 아메리카노"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.itemType()).isEqualTo(SalesItemType.MENU);
                assertThat(item.netAmount()).isZero();
            });
        assertThat(result.orders())
            .filteredOn(order -> order.key().channel() == SalesChannel.POS
                && order.key().orderedAt().getHour() == 11)
            .singleElement()
            .extracting(SalesOrder::valid)
            .isEqualTo(true);
    }

    @Test
    void 주문번호의_앞자리_0을_보존한다() throws Exception {
        TossPosWorkbookData result = parseSynthetic(false, 0);

        assertThat(result.orders())
            .extracting(order -> order.key().posOrderNo())
            .contains("000123");
    }

    @Test
    void 필수_컬럼이_누락되면_파일_전체를_거절한다() throws Exception {
        byte[] workbook = syntheticWorkbook(true, 0);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(workbook)))
            .isInstanceOf(TossPosWorkbookValidationException.class)
            .hasMessageContaining("필수 컬럼")
            .hasMessageContaining("실판매금액");
    }

    @Test
    void 상세_합계와_POS_합계가_다르면_파일_전체를_거절한다() throws Exception {
        byte[] workbook = syntheticWorkbook(false, 1);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(workbook)))
            .isInstanceOf(TossPosWorkbookValidationException.class)
            .hasMessageContaining("실판매금액이 일치하지 않습니다")
            .hasMessageContaining("2026-01-01");
    }

    private TossPosWorkbookData parseSynthetic(
        boolean missingNetAmountColumn,
        long summaryDelta
    ) throws Exception {
        return parser.parse(new ByteArrayInputStream(
            syntheticWorkbook(missingNetAmountColumn, summaryDelta)
        ));
    }

    private byte[] syntheticWorkbook(
        boolean missingNetAmountColumn,
        long summaryDelta
    ) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            createDataBasis(workbook);
            createProductSummary(workbook, summaryDelta);
            createProductDetails(workbook, missingNetAmountColumn);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void createDataBasis(Workbook workbook) {
        Sheet sheet = workbook.createSheet("데이터 기준");
        writeRow(sheet.createRow(0),
            "시작일자", "종료일자", "매출 정산 기준", "매출 시작 시간", "집계 단위");
        writeRow(sheet.createRow(2),
            "2026-01-01", "2026-01-02", "주문한 날", "00:00:00", "일간");
    }

    private void createProductSummary(Workbook workbook, long summaryDelta) {
        Sheet sheet = workbook.createSheet("상품 주문 합계");
        writeRow(sheet.createRow(0),
            "기간", "상품명", "상품코드", "카테고리", "판매건수", "상품가격",
            "옵션가격", "할인", "실 판매 금액\n(할인, 옵션 포함)", "부가세액");
        writeRow(sheet.createRow(1),
            "2026-01-01", "합계", "", "", 1, 3_000, 0, 0, 3_000 + summaryDelta, 273);
        writeRow(sheet.createRow(2),
            "2026-01-02", "합계", "", "", 1, 2_200, 0, 0, 2_200, 200);
    }

    private void createProductDetails(Workbook workbook, boolean missingNetAmountColumn) {
        Sheet sheet = workbook.createSheet("상품 주문 상세내역");
        Object[] headers = {
            "주문기준일자", "결제상태", "주문시작시각", "주문채널", "주문번호",
            "상품명", "상품코드", "카테고리", "옵션", "상품할인", "주문할인", "수량",
            "상품가격", "옵션가격", "상품할인 금액", "주문할인 금액",
            "실판매금액 \n (할인, 옵션 포함)", "과세여부", "부가세액"
        };
        if (missingNetAmountColumn) {
            headers[16] = "";
        }
        writeRow(sheet.createRow(0), headers);
        writeRow(sheet.createRow(1), "", "설명");

        writeDetail(sheet.createRow(2), "2026-01-01", "완료", "2026-01-01 10:00:00",
            "포스", "000123", "  아메리카노 💜  ", "esp", 1, 1_000, 1_000, "과세", 91);
        writeDetail(sheet.createRow(3), "2026-01-01", "취소", "2026-01-01 10:00:00",
            "포스", "000123", "아메리카노", "esp", -1, -1_000, -1_000, "과세", -91);
        writeDetail(sheet.createRow(4), "2026-01-01", "완료", "2026-01-01 11:00:00",
            "포스", "000123", "한입김밥 이벤트 아메리카노", "food", 1, 0, 0, "과세", 0);
        writeDetail(sheet.createRow(5), "2026-01-01", "완료", "2026-01-01 11:00:00",
            "배달", "000123", "배달비", "", 1, 3_000, 3_000, "과세", 273);
        writeDetail(sheet.createRow(6), "2026-01-02", "완료", "2026-01-02 09:00:00",
            "키오스크", "000124", "카페 라떼", "non", 1, 2_200, 2_200, "과세", 200);
    }

    private void writeDetail(
        Row row,
        String orderDate,
        String status,
        String orderedAt,
        String channel,
        String orderNo,
        String menuName,
        String category,
        int quantity,
        long linePrice,
        long netAmount,
        String taxable,
        long vatAmount
    ) {
        writeRow(row,
            orderDate, status, orderedAt, channel, orderNo, menuName, "", category, "", "", "",
            quantity, linePrice, 0, 0, 0, netAmount, taxable, vatAmount);
    }

    private void writeRow(Row row, Object... values) {
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            if (value instanceof Number number) {
                row.createCell(index).setCellValue(number.doubleValue());
            } else {
                row.createCell(index).setCellValue(String.valueOf(value));
            }
        }
    }

    private TossPosItemTypeRules itemTypeRules() {
        return new TossPosItemTypeRules(Map.of(
            SalesItemType.PARKING, List.of(
                "주차권", "주차권 30분", "주차권 1시간", "주차권 4시간", "주차권 24시간"
            ),
            SalesItemType.PREPAID_CARD, List.of(
                "선불카드", "선불카드 (오만원)", "선불카드(만원)", "선불카드(오만원)",
                "선불카드(오천원)", "선불카드(천원)"
            ),
            SalesItemType.DELIVERY_FEE, List.of("배달료", "배달비"),
            SalesItemType.PLATFORM_PLACEHOLDER, List.of("배민1", "쿠팡이츠"),
            SalesItemType.EVENT, List.of("블로그 인스타 체험단", "영수증 포토리뷰 이벤트"),
            SalesItemType.GOODS, List.of(
                "루미코르 코스터", "루미코르 행주타월", "루미코르 행주타월 (걸이형)"
            )
        ));
    }
}
