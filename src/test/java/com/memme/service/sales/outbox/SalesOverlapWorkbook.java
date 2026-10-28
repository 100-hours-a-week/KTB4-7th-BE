package com.memme.service.sales.outbox;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class SalesOverlapWorkbook {
    private SalesOverlapWorkbook() {}

    static byte[] daily(int start, int end, long amount) throws Exception {
        Map<Integer, Long> amounts = new TreeMap<>();
        for (int day = start; day <= end; day++) amounts.put(day, amount);
        return coverage(start, end, amounts);
    }

    static LocalDate date(int day) { return LocalDate.of(2026, 9, day); }

    static byte[] coverage(int start, int end, Map<Integer, Long> amounts) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var basis = workbook.createSheet("데이터 기준");
            row(basis.createRow(0), "시작일자", "종료일자", "매출 정산 기준", "매출 시작 시간", "집계 단위");
            row(basis.createRow(2), date(start).toString(), date(end).toString(), "주문한 날", "00:00:00", "일간");
            var summary = workbook.createSheet("상품 주문 합계");
            row(summary.createRow(0), "기간", "상품명", "상품코드", "카테고리", "판매건수", "상품가격",
                    "옵션가격", "할인", "실 판매 금액\n(할인, 옵션 포함)", "부가세액");
            var detail = workbook.createSheet("상품 주문 상세내역");
            row(detail.createRow(0), "주문기준일자", "결제상태", "주문시작시각", "주문채널", "주문번호",
                    "상품명", "상품코드", "카테고리", "옵션", "상품할인", "주문할인", "수량", "상품가격",
                    "옵션가격", "상품할인 금액", "주문할인 금액", "실판매금액\n(할인, 옵션 포함)", "과세여부", "부가세액");
            row(detail.createRow(1), "", "설명");
            int index = 0;
            for (var entry : new TreeMap<>(amounts).entrySet()) {
                String date = date(entry.getKey()).toString();
                long amount = entry.getValue(), vat = Math.round(amount / 11.0);
                row(summary.createRow(index + 1), date, "합계", "", "", 1, amount, 0, 0, amount, vat);
                row(detail.createRow(index + 2), date, "완료", date + " 12:00:00", "포스", "order-" + entry.getKey(),
                        "아메리카노", "", "커피", "", "", "", 1, amount, 0, 0, 0, amount, "과세", vat);
                index++;
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void row(Row row, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof Number number) row.createCell(i).setCellValue(number.doubleValue());
            else row.createCell(i).setCellValue(values[i].toString());
        }
    }
}
