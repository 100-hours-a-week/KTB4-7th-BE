package com.memme.service.sales.calendar;

import com.memme.dto.sales.SalesCalendarResponse;
import com.memme.dto.common.FieldError;
import com.memme.exception.sales.SalesCalendarInputException;
import com.memme.entity.sales.SalesDailyStatus;
import com.memme.entity.sales.SalesDailySummaryEntity;
import com.memme.entity.solution.WeatherCalendarConditionEntity;
import com.memme.entity.store.Store;
import com.memme.exception.store.StoreProfileNotFoundException;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.solution.WeatherCalendarConditionRepository;
import com.memme.repository.store.StoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalesCalendarQueryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
    private static final String INVALID_RANGE_MESSAGE = "조회 가능한 매출 기간을 벗어났습니다.";
    private static final String INVALID_INPUT_MESSAGE = "조회 월 또는 날짜 형식이 올바르지 않습니다.";
    private static final String DATE_MONTH_MISMATCH_MESSAGE = "선택 날짜가 조회 월에 포함되지 않습니다.";

    private final StoreRepository storeRepository;
    private final SalesDailySummaryRepository summaryRepository;
    private final WeatherCalendarConditionRepository weatherRepository;
    private final Clock clock;

    public SalesCalendarQueryService(
            StoreRepository storeRepository,
            SalesDailySummaryRepository summaryRepository,
            WeatherCalendarConditionRepository weatherRepository,
            Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.summaryRepository = summaryRepository;
        this.weatherRepository = weatherRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SalesCalendarResponse query(Long userId, String monthValue, String selectedDateValue) {
        Store store = storeRepository.findByOwnerId(userId).orElseThrow(StoreProfileNotFoundException::new);
        Long storeId = store.getId();
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth firstMonth = YearMonth.from(store.getCreatedAt());
        List<FieldError> errors = new ArrayList<>();
        YearMonth month = currentMonth;
        LocalDate requestedDate = null;
        try {
            if (monthValue != null) month = parseMonth(monthValue);
        } catch (DateTimeParseException exception) {
            errors.add(new FieldError("month", "조회 월은 YYYY-MM 형식의 유효한 월이어야 합니다."));
        }
        try {
            if (selectedDateValue != null) requestedDate = parseDate(selectedDateValue);
        } catch (DateTimeParseException exception) {
            errors.add(new FieldError("selectedDate", "선택 날짜는 YYYY-MM-DD 형식의 유효한 날짜여야 합니다."));
        }
        if (!errors.isEmpty()) throw new SalesCalendarInputException(errors);

        if (month.isBefore(firstMonth) || month.isAfter(currentMonth)) {
            throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, INVALID_RANGE_MESSAGE);
        }

        LocalDate firstDate = month.atDay(1);
        LocalDate lastDate = month.atEndOfMonth();
        LocalDate selectedDate = selectedDateValue == null
                ? (month.equals(currentMonth) ? today : firstDate)
                : requestedDate;
        if (!YearMonth.from(selectedDate).equals(month)) {
            throw new SalesCalendarInputException(List.of(new FieldError("selectedDate", DATE_MONTH_MISMATCH_MESSAGE)));
        }

        List<SalesDailySummaryEntity> summaries = summaryRepository
                .findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(storeId, firstDate, lastDate);
        Map<LocalDate, SalesDailySummaryEntity> summariesByDate = new HashMap<>();
        summaries.forEach(summary -> summariesByDate.put(summary.getSalesDate(), summary));

        Map<LocalDate, WeatherCalendarConditionEntity> weatherByDate = new HashMap<>();
        if (store.getWeatherStationId() != null) {
            weatherRepository.findAllByStationIdAndObservationDateBetweenOrderByObservationDateAsc(
                            store.getWeatherStationId(), firstDate, lastDate)
                    .forEach(weather -> weatherByDate.put(weather.getObservationDate(), weather));
        }

        List<DayValue> values = new ArrayList<>(month.lengthOfMonth());
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            values.add(toDayValue(date, summariesByDate.get(date), date.isAfter(today)));
        }

        long totalSalesAmount = 0;
        long totalOrderCount = 0;
        long activeSalesTotal = 0;
        int activeDayCount = 0;
        for (DayValue value : values) {
            if (!value.hasSalesActivity()) {
                continue;
            }
            totalSalesAmount = Math.addExact(totalSalesAmount, value.salesAmount());
            totalOrderCount = Math.addExact(totalOrderCount, value.orderCount());
            activeSalesTotal = Math.addExact(activeSalesTotal, value.salesAmount());
            activeDayCount++;
        }
        BigDecimal exactAverage = activeDayCount == 0 ? null : BigDecimal.valueOf(activeSalesTotal)
                .divide(BigDecimal.valueOf(activeDayCount), 16, RoundingMode.HALF_UP);
        Long averageDailySalesAmount = exactAverage == null ? null
                : exactAverage.setScale(0, RoundingMode.HALF_UP).longValueExact();

        List<SalesCalendarResponse.Day> days = new ArrayList<>(values.size());
        Map<LocalDate, SalesCalendarResponse.Day> responseDays = new HashMap<>();
        for (DayValue value : values) {
            SalesCalendarResponse.Day day = toResponseDay(
                    value,
                    today,
                    selectedDate,
                    exactAverage,
                    weatherByDate.get(value.date())
            );
            days.add(day);
            responseDays.put(value.date(), day);
        }

        SalesCalendarResponse.Day selected = responseDays.get(selectedDate);
        SalesCalendarResponse.SelectedDay selectedDay = new SalesCalendarResponse.SelectedDay(
                selectedDate,
                selected.dataStatus(),
                selected.salesAmount(),
                selected.orderCount(),
                averageOrderAmount(selected.salesAmount(), selected.orderCount()),
                new SalesCalendarResponse.Insight(
                        selected.salesLevel(),
                        averageDailySalesAmount,
                        deviationRate(selected.salesAmount(), exactAverage)
                )
        );

        return new SalesCalendarResponse(
                month.toString(),
                SEOUL.getId(),
                new SalesCalendarResponse.AvailableRange(firstMonth.toString(), currentMonth.toString()),
                selectedDate,
                new SalesCalendarResponse.MonthSummary(
                        totalSalesAmount,
                        totalOrderCount,
                        averageDailySalesAmount,
                        activeDayCount
                ),
                days,
                selectedDay
        );
    }

    @Transactional(readOnly = true)
    public SalesCalendarResponse.SelectedDay queryDay(Long userId, String dateValue) {
        LocalDate date;
        try {
            date = parseDate(dateValue);
        } catch (DateTimeParseException exception) {
            throw new SalesCalendarInputException(List.of(new FieldError("date", "조회 날짜 형식이 올바르지 않습니다.")));
        }
        return query(userId, YearMonth.from(date).toString(), date.toString()).selectedDay();
    }

    private DayValue toDayValue(LocalDate date, SalesDailySummaryEntity summary, boolean future) {
        if (future) {
            return new DayValue(date, "FUTURE", null, null, 0, false);
        }
        if (summary == null) {
            return new DayValue(date, "UNKNOWN", null, null, 0, false);
        }
        SalesDailyStatus status = summary.getDayStatus();
        boolean ready = status == SalesDailyStatus.COMPLETE;
        int activityCount = summary.getSalesActivityCount();
        boolean hasSalesActivity = ready && activityCount > 0;
        if (!ready) {
            return new DayValue(date, status.name(), null, null, activityCount, false);
        }
        if (activityCount == 0 && summary.getOrderCount() > 0) {
            return new DayValue(date, "UNKNOWN", null, null, activityCount, false);
        }
        if (!hasSalesActivity) {
            return new DayValue(date, status.name(), null, null, activityCount, false);
        }
        return new DayValue(
                date,
                status.name(),
                summary.getTotalNetAmount(),
                summary.getOrderCount(),
                activityCount,
                true
        );
    }

    private SalesCalendarResponse.Day toResponseDay(
            DayValue value,
            LocalDate today,
            LocalDate selectedDate,
            BigDecimal monthlyAverage,
            WeatherCalendarConditionEntity weather
    ) {
        return new SalesCalendarResponse.Day(
                value.date(),
                value.date().getDayOfWeek().name(),
                value.date().equals(today),
                value.date().equals(selectedDate),
                value.dataStatus(),
                value.salesAmount(),
                value.orderCount(),
                salesLevel(value, monthlyAverage),
                weather == null ? null : new SalesCalendarResponse.Weather(weather.getCondition().name())
        );
    }

    private static String salesLevel(DayValue day, BigDecimal monthlyAverage) {
        if (!day.hasSalesActivity()) {
            return "UNAVAILABLE";
        }
        if (monthlyAverage == null || monthlyAverage.signum() <= 0) {
            return "UNAVAILABLE";
        }
        if (BigDecimal.valueOf(day.salesAmount()).compareTo(monthlyAverage.multiply(
                new BigDecimal("1.20"))) >= 0) {
            return "HIGH";
        }
        if (BigDecimal.valueOf(day.salesAmount()).compareTo(monthlyAverage.multiply(
                new BigDecimal("0.80"))) < 0) {
            return "LOW";
        }
        return "NORMAL";
    }

    private static Long averageOrderAmount(Long salesAmount, Integer orderCount) {
        if (salesAmount == null || orderCount == null || orderCount == 0) {
            return null;
        }
        return Math.round((double) salesAmount / orderCount);
    }

    private static BigDecimal deviationRate(Long salesAmount, BigDecimal monthlyAverage) {
        if (salesAmount == null || monthlyAverage == null || monthlyAverage.signum() <= 0) {
            return null;
        }
        return BigDecimal.valueOf(salesAmount)
                .subtract(monthlyAverage)
                .divide(monthlyAverage, 4, RoundingMode.HALF_UP);
    }

    private static YearMonth parseMonth(String value) {
        if (!value.matches("\\d{4}-\\d{2}")) {
            throw new DateTimeParseException(INVALID_INPUT_MESSAGE, value, 0);
        }
        try {
            return YearMonth.parse(value, MONTH_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new DateTimeParseException(INVALID_INPUT_MESSAGE, value, 0);
        }
    }

    private static LocalDate parseDate(String value) {
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new DateTimeParseException(INVALID_INPUT_MESSAGE, value, 0);
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new DateTimeParseException(INVALID_INPUT_MESSAGE, value, 0);
        }
    }

    private record DayValue(
            LocalDate date,
            String dataStatus,
            Long salesAmount,
            Integer orderCount,
            int salesActivityCount,
            boolean hasSalesActivity
    ) {
        private DayValue {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(dataStatus, "dataStatus");
        }
    }
}
