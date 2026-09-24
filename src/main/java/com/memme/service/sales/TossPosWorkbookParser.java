package com.memme.service.sales;

import com.memme.exception.sales.TossPosWorkbookValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public final class TossPosWorkbookParser {

    private static final String DATA_BASIS_SHEET = "데이터 기준";
    private static final String PRODUCT_SUMMARY_SHEET = "상품 주문 합계";
    private static final String PRODUCT_DETAIL_SHEET = "상품 주문 상세내역";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
        .ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
        .ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter
        .ofPattern("HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    private static final Set<String> DATA_BASIS_COLUMNS = Set.of(
        "시작일자", "종료일자", "매출 정산 기준", "매출 시작 시간"
    );
    private static final Set<String> DETAIL_COLUMNS = Set.of(
        "주문기준일자", "결제상태", "주문시작시각", "주문채널", "주문번호",
        "상품명", "카테고리", "옵션", "상품할인", "주문할인", "수량", "상품가격",
        "옵션가격", "상품할인 금액", "주문할인 금액", "실판매금액 (할인, 옵션 포함)",
        "과세여부", "부가세액"
    );
    private static final Set<String> SUMMARY_COLUMNS = Set.of(
        "기간", "실 판매 금액 (할인, 옵션 포함)"
    );

    private final TossPosItemTypeRules itemTypeRules;

    public TossPosWorkbookParser(TossPosItemTypeRules itemTypeRules) {
        this.itemTypeRules = Objects.requireNonNull(itemTypeRules, "itemTypeRules");
    }

    public TossPosWorkbookData parse(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");

        try (OPCPackage opcPackage = OPCPackage.open(inputStream)) {
            XSSFReader reader = new XSSFReader(opcPackage, true);

            DataBasisSheetHandler dataBasisHandler = new DataBasisSheetHandler();
            parseSheet(reader, DATA_BASIS_SHEET, dataBasisHandler);
            DataBasis dataBasis = dataBasisHandler.result();

            DetailSheetHandler detailHandler = new DetailSheetHandler(dataBasis);
            parseSheet(reader, PRODUCT_DETAIL_SHEET, detailHandler);
            List<SalesOrderItem> items = detailHandler.result();

            SummarySheetHandler summaryHandler = new SummarySheetHandler(dataBasis);
            parseSheet(reader, PRODUCT_SUMMARY_SHEET, summaryHandler);
            Map<LocalDate, Long> posSummary = summaryHandler.result();

            validateProductSummary(items, posSummary);
            List<SalesOrder> orders = groupOrders(items);
            LocalDate coverageStart = items.stream()
                .map(SalesOrderItem::orderDate)
                .min(LocalDate::compareTo)
                .orElseThrow(() -> validation("상품 주문 상세내역에 데이터 행이 없습니다."));

            return new TossPosWorkbookData(
                coverageStart,
                dataBasis.periodEnd(),
                orders,
                items,
                aggregateDaily(items, orders, coverageStart, dataBasis.periodEnd())
            );
        } catch (TossPosWorkbookValidationException exception) {
            throw exception;
        } catch (OpenXML4JException | SAXException | ParserConfigurationException exception) {
            throw new TossPosWorkbookValidationException(
                "유효한 토스 POS .xlsx 파일이 아닙니다.", exception
            );
        } catch (RuntimeException exception) {
            throw new TossPosWorkbookValidationException(
                "토스 POS 엑셀 파일을 읽는 중 오류가 발생했습니다.", exception
            );
        }
    }

    private void parseSheet(
        XSSFReader reader,
        String requiredSheetName,
        XSSFSheetXMLHandler.SheetContentsHandler contentsHandler
    ) throws IOException, OpenXML4JException, SAXException, ParserConfigurationException {
        StylesTable styles = reader.getStylesTable();
        SharedStrings sharedStrings = reader.getSharedStringsTable();
        XSSFReader.SheetIterator sheets = reader.getSheetIterator();
        boolean found = false;

        while (sheets.hasNext()) {
            try (InputStream sheetStream = sheets.next()) {
                if (!requiredSheetName.equals(sheets.getSheetName())) {
                    continue;
                }
                found = true;
                XMLReader xmlReader = XMLHelper.newXMLReader();
                xmlReader.setContentHandler(new XSSFSheetXMLHandler(
                    styles,
                    sharedStrings,
                    contentsHandler,
                    new DataFormatter(Locale.KOREA, true),
                    false
                ));
                xmlReader.parse(new InputSource(sheetStream));
                break;
            }
        }
        if (!found) {
            throw validation("필수 시트가 없습니다: " + requiredSheetName);
        }
    }

    private SalesOrderItem readItem(
        String sheetName,
        SheetRow row,
        Map<String, Integer> columns,
        DataBasis dataBasis
    ) {
        LocalDate orderDate = parseDate(requiredText(sheetName, row, columns, "주문기준일자"), sheetName, row, "주문기준일자");
        SalesOrderStatus status = parseStatus(requiredText(sheetName, row, columns, "결제상태"), sheetName, row);
        LocalDateTime orderedAt = parseDateTime(requiredText(sheetName, row, columns, "주문시작시각"), sheetName, row, "주문시작시각");
        SalesChannel channel = parseChannel(requiredText(sheetName, row, columns, "주문채널"), sheetName, row);
        String posOrderNo = requiredText(sheetName, row, columns, "주문번호");
        String menuNameRaw = requiredText(sheetName, row, columns, "상품명");
        String menuName = TossPosTextNormalizer.menuName(menuNameRaw);
        String menuKey = TossPosTextNormalizer.menuKey(menuNameRaw);
        String categoryRaw = optionalText(row, columns, "카테고리");
        String optionRaw = optionalText(row, columns, "옵션");
        int quantity = parseInteger(requiredText(sheetName, row, columns, "수량"), sheetName, row, "수량");
        long linePrice = parseLong(requiredText(sheetName, row, columns, "상품가격"), sheetName, row, "상품가격");
        long optionPrice = parseLong(requiredText(sheetName, row, columns, "옵션가격"), sheetName, row, "옵션가격");
        String itemDiscountName = optionalText(row, columns, "상품할인");
        long itemDiscountAmount = parseLong(requiredText(sheetName, row, columns, "상품할인 금액"), sheetName, row, "상품할인 금액");
        String orderDiscountName = optionalText(row, columns, "주문할인");
        long orderDiscountAmount = parseLong(requiredText(sheetName, row, columns, "주문할인 금액"), sheetName, row, "주문할인 금액");
        long netAmount = parseLong(requiredText(sheetName, row, columns, "실판매금액 (할인, 옵션 포함)"), sheetName, row, "실판매금액");
        boolean taxable = parseTaxable(requiredText(sheetName, row, columns, "과세여부"), sheetName, row);
        long vatAmount = parseLong(requiredText(sheetName, row, columns, "부가세액"), sheetName, row, "부가세액");

        if (menuName.isBlank() || menuKey.isBlank()) {
            throw rowValidation(sheetName, row, "상품명을 정규화한 결과가 비어 있습니다.");
        }
        if (quantity == 0) {
            throw rowValidation(sheetName, row, "수량은 0일 수 없습니다.");
        }
        if (status == SalesOrderStatus.CANCELED && quantity >= 0) {
            throw rowValidation(sheetName, row, "취소 행의 수량은 음수여야 합니다.");
        }
        if (!orderDate.equals(orderedAt.toLocalDate())) {
            throw rowValidation(sheetName, row, "주문기준일자와 주문시작시각의 날짜가 일치하지 않습니다.");
        }
        if (orderDate.isBefore(dataBasis.periodStart()) || orderDate.isAfter(dataBasis.periodEnd())) {
            throw rowValidation(sheetName, row, "주문기준일자가 데이터 기준 기간을 벗어났습니다.");
        }

        long expectedNetAmount;
        try {
            expectedNetAmount = Math.addExact(
                Math.addExact(linePrice, optionPrice),
                Math.addExact(itemDiscountAmount, orderDiscountAmount)
            );
        } catch (ArithmeticException exception) {
            throw rowValidation(sheetName, row, "금액 합산 범위를 초과했습니다.");
        }
        if (netAmount != expectedNetAmount) {
            throw rowValidation(sheetName, row, "실판매금액 계산이 일치하지 않습니다.");
        }
        long expectedVat = taxable ? roundedVat(netAmount) : 0L;
        if (vatAmount != expectedVat) {
            throw rowValidation(sheetName, row, "부가세액 계산이 일치하지 않습니다.");
        }
        if (linePrice % quantity != 0) {
            throw rowValidation(sheetName, row, "상품가격을 수량으로 나눈 단가가 원 단위 정수가 아닙니다.");
        }

        SalesOrderKey orderKey = new SalesOrderKey(channel, posOrderNo, orderedAt);
        return new SalesOrderItem(
            orderKey, orderDate, status, menuNameRaw, menuName, menuKey, categoryRaw,
            standardCategory(categoryRaw), optionRaw, quantity, linePrice, linePrice / quantity,
            optionPrice, itemDiscountName, itemDiscountAmount, orderDiscountName,
            orderDiscountAmount, netAmount, taxable, vatAmount, itemTypeRules.classify(menuKey)
        );
    }

    private void validateProductSummary(
        List<SalesOrderItem> items,
        Map<LocalDate, Long> posSummary
    ) {
        Map<LocalDate, Long> detailSummary = new HashMap<>();
        for (SalesOrderItem item : items) {
            detailSummary.merge(item.orderDate(), item.netAmount(), TossPosWorkbookParser::addExact);
        }
        Set<LocalDate> dates = new HashSet<>(detailSummary.keySet());
        dates.addAll(posSummary.keySet());
        for (LocalDate date : dates) {
            long detailAmount = detailSummary.getOrDefault(date, 0L);
            long summaryAmount = posSummary.getOrDefault(date, 0L);
            if (detailAmount != summaryAmount) {
                throw validation(
                    "상품 주문 상세내역과 상품 주문 합계의 실판매금액이 일치하지 않습니다. "
                        + "기간=" + date + ", 상세=" + detailAmount + ", 합계=" + summaryAmount
                );
            }
        }
    }

    private List<SalesOrder> groupOrders(List<SalesOrderItem> items) {
        Map<SalesOrderKey, List<SalesOrderItem>> grouped = new LinkedHashMap<>();
        for (SalesOrderItem item : items) {
            grouped.computeIfAbsent(item.orderKey(), ignored -> new ArrayList<>()).add(item);
        }
        List<SalesOrder> orders = new ArrayList<>(grouped.size());
        for (Map.Entry<SalesOrderKey, List<SalesOrderItem>> entry : grouped.entrySet()) {
            long menuQuantity = entry.getValue().stream()
                .filter(item -> item.itemType() == SalesItemType.MENU)
                .mapToLong(SalesOrderItem::quantity)
                .sum();
            orders.add(new SalesOrder(entry.getKey(), menuQuantity > 0, entry.getValue()));
        }
        return List.copyOf(orders);
    }

    private List<DailySalesSummary> aggregateDaily(
        List<SalesOrderItem> items,
        List<SalesOrder> orders,
        LocalDate coverageStart,
        LocalDate coverageEnd
    ) {
        Map<LocalDate, MutableDailySummary> summaries = new TreeMap<>();
        for (LocalDate date = coverageStart; !date.isAfter(coverageEnd); date = date.plusDays(1)) {
            summaries.put(date, new MutableDailySummary());
        }
        for (SalesOrderItem item : items) {
            MutableDailySummary summary = summaries.get(item.orderDate());
            summary.totalNetAmount = addExact(summary.totalNetAmount, item.netAmount());
            if (item.itemType() == SalesItemType.MENU) {
                summary.menuNetAmount = addExact(summary.menuNetAmount, item.netAmount());
                summary.menuQuantity = Math.addExact(summary.menuQuantity, item.quantity());
            }
        }
        for (SalesOrder order : orders) {
            if (order.valid()) {
                MutableDailySummary summary = summaries.get(order.items().getFirst().orderDate());
                summary.orderCount = Math.incrementExact(summary.orderCount);
            }
        }
        return summaries.entrySet().stream()
            .map(entry -> new DailySalesSummary(
                entry.getKey(), entry.getValue().totalNetAmount, entry.getValue().menuNetAmount,
                entry.getValue().orderCount, entry.getValue().menuQuantity
            ))
            .toList();
    }

    private Map<String, Integer> readColumns(
        String sheetName,
        SheetRow header,
        Collection<String> requiredColumns
    ) {
        Map<String, Integer> columns = new HashMap<>();
        header.values().forEach((index, value) -> {
            String name = TossPosTextNormalizer.header(value);
            if (!name.isBlank()) {
                columns.putIfAbsent(name, index);
            }
        });
        List<String> missing = requiredColumns.stream()
            .filter(column -> !columns.containsKey(column)).sorted().toList();
        if (!missing.isEmpty()) {
            throw validation(sheetName + " 시트에 필수 컬럼이 없습니다: " + String.join(", ", missing));
        }
        return Map.copyOf(columns);
    }

    private String requiredText(
        String sheetName,
        SheetRow row,
        Map<String, Integer> columns,
        String column
    ) {
        String value = row.value(columns.get(column)).trim();
        if (value.isEmpty()) {
            throw rowValidation(sheetName, row, column + " 값이 비어 있습니다.");
        }
        return value;
    }

    private String optionalText(SheetRow row, Map<String, Integer> columns, String column) {
        return row.value(columns.get(column)).trim();
    }

    private LocalDate parseDate(String value, String sheetName, SheetRow row, String column) {
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw rowValidation(sheetName, row, column + "의 날짜 형식은 YYYY-MM-DD여야 합니다.");
        }
    }

    private LocalDateTime parseDateTime(
        String value,
        String sheetName,
        SheetRow row,
        String column
    ) {
        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw rowValidation(sheetName, row, column + "의 일시 형식은 YYYY-MM-DD HH:MM:SS여야 합니다.");
        }
    }

    private LocalTime parseTime(String value, String sheetName, SheetRow row, String column) {
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw rowValidation(sheetName, row, column + "의 시각 형식은 HH:MM:SS여야 합니다.");
        }
    }

    private int parseInteger(String value, String sheetName, SheetRow row, String column) {
        long parsed = parseLong(value, sheetName, row, column);
        try {
            return Math.toIntExact(parsed);
        } catch (ArithmeticException exception) {
            throw rowValidation(sheetName, row, column + " 값이 정수 범위를 벗어났습니다.");
        }
    }

    private long parseLong(String value, String sheetName, SheetRow row, String column) {
        try {
            return new BigDecimal(value.replace(",", "")).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw rowValidation(sheetName, row, column + " 값은 원 단위 정수여야 합니다.");
        }
    }

    private SalesOrderStatus parseStatus(String value, String sheetName, SheetRow row) {
        return switch (value) {
            case "완료" -> SalesOrderStatus.COMPLETED;
            case "취소" -> SalesOrderStatus.CANCELED;
            default -> throw rowValidation(sheetName, row, "결제상태는 완료 또는 취소여야 합니다.");
        };
    }

    private SalesChannel parseChannel(String value, String sheetName, SheetRow row) {
        return switch (value) {
            case "키오스크" -> SalesChannel.KIOSK;
            case "포스" -> SalesChannel.POS;
            case "배달" -> SalesChannel.DELIVERY;
            default -> throw rowValidation(sheetName, row, "주문채널은 키오스크, 포스, 배달 중 하나여야 합니다.");
        };
    }

    private boolean parseTaxable(String value, String sheetName, SheetRow row) {
        return switch (value) {
            case "과세" -> true;
            case "면세" -> false;
            default -> throw rowValidation(sheetName, row, "과세여부는 과세 또는 면세여야 합니다.");
        };
    }

    private StandardMenuCategory standardCategory(String rawCategory) {
        return switch (TossPosTextNormalizer.category(rawCategory)) {
            case "esp" -> StandardMenuCategory.COFFEE;
            case "non" -> StandardMenuCategory.NON_COFFEE;
            case "teabag" -> StandardMenuCategory.TEA;
            case "ade" -> StandardMenuCategory.ADE;
            case "smoothie" -> StandardMenuCategory.SMOOTHIE;
            case "beverage" -> StandardMenuCategory.BOTTLED_DRINK;
            case "beer" -> StandardMenuCategory.BEER;
            case "cake" -> StandardMenuCategory.CAKE;
            case "bakery" -> StandardMenuCategory.BAKERY;
            case "food", "snack" -> StandardMenuCategory.FOOD;
            case "루미코르 팝업", "신메뉴 라인업", "신메뉴 우베라인업" -> StandardMenuCategory.SEASONAL;
            default -> StandardMenuCategory.UNMAPPED;
        };
    }

    private long roundedVat(long netAmount) {
        return BigDecimal.valueOf(netAmount)
            .divide(BigDecimal.valueOf(11), 0, RoundingMode.HALF_UP).longValueExact();
    }

    private static long addExact(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw validation("금액 합산 범위를 초과했습니다.");
        }
    }

    private TossPosWorkbookValidationException rowValidation(
        String sheetName,
        SheetRow row,
        String message
    ) {
        return validation(sheetName + " 시트 " + (row.rowNumber() + 1) + "행: " + message);
    }

    private static TossPosWorkbookValidationException validation(String message) {
        return new TossPosWorkbookValidationException(message);
    }

    private final class DataBasisSheetHandler extends StreamingSheetHandler {

        private Map<String, Integer> columns;
        private DataBasis dataBasis;

        @Override
        protected void accept(SheetRow row) {
            if (row.rowNumber() == 0) {
                columns = readColumns(DATA_BASIS_SHEET, row, DATA_BASIS_COLUMNS);
            } else if (row.rowNumber() == 2) {
                if (columns == null) {
                    throw validation("데이터 기준 시트의 컬럼 행이 없습니다.");
                }
                LocalDate periodStart = parseDate(requiredText(DATA_BASIS_SHEET, row, columns, "시작일자"), DATA_BASIS_SHEET, row, "시작일자");
                LocalDate periodEnd = parseDate(requiredText(DATA_BASIS_SHEET, row, columns, "종료일자"), DATA_BASIS_SHEET, row, "종료일자");
                String settlementBasis = requiredText(DATA_BASIS_SHEET, row, columns, "매출 정산 기준");
                LocalTime startTime = parseTime(requiredText(DATA_BASIS_SHEET, row, columns, "매출 시작 시간"), DATA_BASIS_SHEET, row, "매출 시작 시간");
                if (periodStart.isAfter(periodEnd)) {
                    throw validation("데이터 기준의 시작일자가 종료일자보다 늦습니다.");
                }
                if (!"주문한 날".equals(settlementBasis)) {
                    throw validation("데이터 기준의 매출 정산 기준은 '주문한 날'이어야 합니다.");
                }
                if (!LocalTime.MIDNIGHT.equals(startTime)) {
                    throw validation("데이터 기준의 매출 시작 시간은 '00:00:00'이어야 합니다.");
                }
                dataBasis = new DataBasis(periodStart, periodEnd);
            }
        }

        private DataBasis result() {
            if (dataBasis == null) {
                throw validation("데이터 기준 시트의 3행이 비어 있습니다.");
            }
            return dataBasis;
        }
    }

    private final class DetailSheetHandler extends StreamingSheetHandler {

        private final DataBasis dataBasis;
        private final List<SalesOrderItem> items = new ArrayList<>();
        private Map<String, Integer> columns;

        private DetailSheetHandler(DataBasis dataBasis) {
            this.dataBasis = dataBasis;
        }

        @Override
        protected void accept(SheetRow row) {
            if (row.rowNumber() == 0) {
                columns = readColumns(PRODUCT_DETAIL_SHEET, row, DETAIL_COLUMNS);
            } else if (row.rowNumber() >= 2 && !row.isBlank()) {
                if (columns == null) {
                    throw validation("상품 주문 상세내역 시트의 컬럼 행이 없습니다.");
                }
                items.add(readItem(PRODUCT_DETAIL_SHEET, row, columns, dataBasis));
            }
        }

        private List<SalesOrderItem> result() {
            if (items.isEmpty()) {
                throw validation("상품 주문 상세내역에 데이터 행이 없습니다.");
            }
            return List.copyOf(items);
        }
    }

    private final class SummarySheetHandler extends StreamingSheetHandler {

        private final DataBasis dataBasis;
        private final Map<LocalDate, Long> summary = new HashMap<>();
        private Map<String, Integer> columns;

        private SummarySheetHandler(DataBasis dataBasis) {
            this.dataBasis = dataBasis;
        }

        @Override
        protected void accept(SheetRow row) {
            if (row.rowNumber() == 0) {
                columns = readColumns(PRODUCT_SUMMARY_SHEET, row, SUMMARY_COLUMNS);
            } else if (!row.isBlank()) {
                if (columns == null) {
                    throw validation("상품 주문 합계 시트의 컬럼 행이 없습니다.");
                }
                LocalDate date = parseDate(requiredText(PRODUCT_SUMMARY_SHEET, row, columns, "기간"), PRODUCT_SUMMARY_SHEET, row, "기간");
                if (date.isBefore(dataBasis.periodStart()) || date.isAfter(dataBasis.periodEnd())) {
                    throw rowValidation(PRODUCT_SUMMARY_SHEET, row, "기간이 데이터 기준 범위를 벗어났습니다.");
                }
                long amount = parseLong(
                    requiredText(PRODUCT_SUMMARY_SHEET, row, columns, "실 판매 금액 (할인, 옵션 포함)"),
                    PRODUCT_SUMMARY_SHEET, row, "실 판매 금액"
                );
                summary.merge(date, amount, TossPosWorkbookParser::addExact);
            }
        }

        private Map<LocalDate, Long> result() {
            if (summary.isEmpty()) {
                throw validation("상품 주문 합계에 데이터 행이 없습니다.");
            }
            return Map.copyOf(summary);
        }
    }

    private abstract static class StreamingSheetHandler
        implements XSSFSheetXMLHandler.SheetContentsHandler {

        private int rowNumber;
        private Map<Integer, String> values;

        @Override
        public final void startRow(int rowNumber) {
            this.rowNumber = rowNumber;
            this.values = new HashMap<>();
        }

        @Override
        public final void endRow(int rowNumber) {
            accept(new SheetRow(this.rowNumber, Map.copyOf(values)));
        }

        @Override
        public final void cell(String cellReference, String value, XSSFComment comment) {
            values.put(columnIndex(cellReference), value == null ? "" : value);
        }

        protected abstract void accept(SheetRow row);

        private static int columnIndex(String cellReference) {
            int column = 0;
            for (int index = 0; index < cellReference.length(); index++) {
                char character = cellReference.charAt(index);
                if (character < 'A' || character > 'Z') {
                    break;
                }
                column = column * 26 + character - 'A' + 1;
            }
            return column - 1;
        }
    }

    private record SheetRow(int rowNumber, Map<Integer, String> values) {

        private String value(int column) {
            return values.getOrDefault(column, "");
        }

        private boolean isBlank() {
            return values.values().stream().allMatch(String::isBlank);
        }
    }

    private record DataBasis(LocalDate periodStart, LocalDate periodEnd) {
    }

    private static final class MutableDailySummary {

        private long totalNetAmount;
        private long menuNetAmount;
        private int orderCount;
        private int menuQuantity;
    }
}
