package com.memme.service.solution.external;

import com.memme.dto.sales.SalesSolutionMetrics.WeatherContext;
import com.memme.dto.solution.external.KmaVillageForecastResponse;
import com.memme.dto.solution.external.KmaVillageForecastResponse.Item;
import com.memme.entity.store.StoreBusinessHours;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class WeatherContextSummaryService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KMA_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter KMA_TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter BASIS_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int RAIN_PROBABILITY_THRESHOLD = 60;
    private static final int HOT_TEMPERATURE_THRESHOLD_C = 33;
    private static final int COLD_TEMPERATURE_THRESHOLD_C = -12;

    private final Clock clock;

    public WeatherContextSummaryService() {
        this(Clock.system(KOREA));
    }

    WeatherContextSummaryService(Clock clock) {
        this.clock = clock;
    }

    public boolean needsDailyTemperatureSupplement(KmaVillageForecastResponse forecast, LocalDate targetDate) {
        if (forecast == null || targetDate == null || forecast.response() == null
                || forecast.response().body() == null || forecast.response().body().items() == null
                || forecast.response().body().items().item() == null) {
            return false;
        }
        return dailyTemperatures(forecast.response().body().items().item(), targetDate).isEmpty();
    }

    public Optional<WeatherContext> summarize(
            KmaVillageForecastResponse forecast,
            List<StoreBusinessHours> businessHours
    ) {
        return summarize(forecast, null, businessHours);
    }

    public Optional<WeatherContext> summarize(
            KmaVillageForecastResponse forecast,
            KmaVillageForecastResponse earlierForecast,
            List<StoreBusinessHours> businessHours
    ) {
        if (forecast == null || businessHours == null || forecast.response() == null
                || forecast.response().body() == null || forecast.response().body().items() == null
                || forecast.response().body().items().item() == null) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now(clock.withZone(KOREA));
        LocalDate today = now.toLocalDate();
        List<OperatingWindow> operatingWindows = operatingWindows(businessHours, today, now);
        if (operatingWindows.isEmpty()) {
            return Optional.empty();
        }

        List<Item> items = forecast.response().body().items().item();
        Map<LocalTime, Map<String, String>> hourlyValues = new HashMap<>();
        for (Item item : items) {
            if (item == null || item.fcstDate() == null || item.fcstTime() == null || item.category() == null) {
                continue;
            }
            if (!today.equals(parseDate(item.fcstDate()))) {
                continue;
            }
            LocalTime forecastTime = parseTime(item.fcstTime());
            if (forecastTime == null) {
                continue;
            }
            hourlyValues.computeIfAbsent(forecastTime, ignored -> new HashMap<>())
                    .put(item.category(), item.fcstValue());
        }

        List<ForecastHour> remainingBusinessHours = hourlyValues.entrySet().stream()
                .filter(entry -> overlapsBusinessWindow(today.atTime(entry.getKey()), operatingWindows))
                .map(entry -> new ForecastHour(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ForecastHour::time))
                .toList();
        if (remainingBusinessHours.isEmpty()) {
            return Optional.empty();
        }

        Optional<DailyTemperatures> dailyTemperatures = dailyTemperatures(items, today);
        if (dailyTemperatures.isEmpty() && earlierForecast != null
                && earlierForecast.response() != null && earlierForecast.response().body() != null
                && earlierForecast.response().body().items() != null
                && earlierForecast.response().body().items().item() != null) {
            dailyTemperatures = dailyTemperatures(earlierForecast.response().body().items().item(), today);
        }
        Optional<String> basisDateTime = basisDateTime(items);
        if (dailyTemperatures.isEmpty() || basisDateTime.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Integer> skyCounts = new LinkedHashMap<>();
        List<Integer> rainHours = new ArrayList<>();
        double maxPop = 0.0;
        boolean isNotable = false;
        BigDecimal precipitationTotal = BigDecimal.ZERO;
        boolean exactPrecipitation = true;

        for (ForecastHour hour : remainingBusinessHours) {
            String skyCode = hour.values().get("SKY");
            Double pop = parseNumber(hour.values().get("POP"));
            Integer temperature = parseTemperature(hour.values().get("TMP"));
            Integer precipitationType = parseInteger(hour.values().get("PTY"));
            if (skyCode == null || pop == null || temperature == null || precipitationType == null
                    || !Double.isFinite(pop) || pop < 0.0 || pop > 100.0
                    || precipitationType < 0 || precipitationType > 7) {
                return Optional.empty();
            }

            String skyText = skyText(skyCode);
            if (skyText == null) {
                return Optional.empty();
            }
            skyCounts.merge(skyText, 1, Integer::sum);
            maxPop = Math.max(maxPop, pop / 100.0);
            if (pop >= RAIN_PROBABILITY_THRESHOLD) {
                rainHours.add(hour.time().getHour());
                isNotable = true;
            }
            if (precipitationType != 0 || temperature >= HOT_TEMPERATURE_THRESHOLD_C
                    || temperature <= COLD_TEMPERATURE_THRESHOLD_C) {
                isNotable = true;
            }

            BigDecimal hourlyPrecipitation = parseExactPrecipitation(hour.values().get("PCP"));
            if (hourlyPrecipitation == null) {
                exactPrecipitation = false;
            } else {
                precipitationTotal = precipitationTotal.add(hourlyPrecipitation);
            }
        }

        String representativeSky = representativeSky(skyCounts);
        if (representativeSky == null) {
            return Optional.empty();
        }

        return Optional.of(new WeatherContext(
                basisDateTime.get(),
                representativeSky,
                dailyTemperatures.get().minimumC(),
                dailyTemperatures.get().maximumC(),
                maxPop,
                List.copyOf(rainHours),
                exactPrecipitation ? precipitationTotal.doubleValue() : null,
                isNotable
        ));
    }

    private List<OperatingWindow> operatingWindows(
            List<StoreBusinessHours> businessHours,
            LocalDate today,
            LocalDateTime now
    ) {
        List<OperatingWindow> dateWindows = new ArrayList<>();
        businessHoursForDay(businessHours, today)
                .flatMap(hours -> todayWindow(hours, today))
                .ifPresent(dateWindows::add);
        businessHoursForDay(businessHours, today.minusDays(1))
                .flatMap(hours -> overnightTail(hours, today))
                .ifPresent(dateWindows::add);

        return dateWindows.stream()
                .filter(window -> now.isBefore(window.end()))
                .map(window -> new OperatingWindow(
                        now.isAfter(window.start()) ? now : window.start(),
                        window.end()
                ))
                .filter(window -> window.start().isBefore(window.end()))
                .toList();
    }

    private Optional<StoreBusinessHours> businessHoursForDay(
            List<StoreBusinessHours> businessHours,
            LocalDate date
    ) {
        List<StoreBusinessHours> matchingHours = businessHours.stream()
                .filter(hours -> hours != null && Integer.valueOf(date.getDayOfWeek().getValue())
                        .equals(hours.getDayOfWeek()))
                .toList();
        return matchingHours.size() == 1 ? Optional.of(matchingHours.getFirst()) : Optional.empty();
    }

    private Optional<OperatingWindow> todayWindow(StoreBusinessHours hours, LocalDate today) {
        LocalTime opensAt = hours.getOpensAt();
        LocalTime closesAt = hours.getClosesAt();
        if (Boolean.TRUE.equals(hours.isClosed()) || opensAt == null || closesAt == null) {
            return Optional.empty();
        }
        if (opensAt.equals(LocalTime.MIDNIGHT) && closesAt.equals(LocalTime.MIDNIGHT)) {
            return Optional.of(new OperatingWindow(today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        }
        if (closesAt.equals(opensAt)) {
            return Optional.empty();
        }
        LocalDateTime end = closesAt.isBefore(opensAt)
                ? today.plusDays(1).atStartOfDay()
                : today.atTime(closesAt);
        return Optional.of(new OperatingWindow(today.atTime(opensAt), end));
    }

    private Optional<OperatingWindow> overnightTail(StoreBusinessHours hours, LocalDate today) {
        LocalTime opensAt = hours.getOpensAt();
        LocalTime closesAt = hours.getClosesAt();
        if (Boolean.TRUE.equals(hours.isClosed()) || opensAt == null || closesAt == null
                || !closesAt.isBefore(opensAt)) {
            return Optional.empty();
        }
        return Optional.of(new OperatingWindow(today.atStartOfDay(), today.atTime(closesAt)));
    }

    private boolean overlapsBusinessWindow(LocalDateTime forecastHour, List<OperatingWindow> windows) {
        LocalDateTime forecastHourEnd = forecastHour.plusHours(1);
        return windows.stream().anyMatch(window -> forecastHourEnd.isAfter(window.start())
                && forecastHour.isBefore(window.end()));
    }

    private Optional<DailyTemperatures> dailyTemperatures(List<Item> items, LocalDate today) {
        List<Integer> minimums = new ArrayList<>();
        List<Integer> maximums = new ArrayList<>();
        for (Item item : items) {
            if (item == null || !today.equals(parseDate(item.fcstDate()))) {
                continue;
            }
            if ("TMN".equals(item.category())) {
                Integer value = parseTemperature(item.fcstValue());
                if (value != null) {
                    minimums.add(value);
                }
            } else if ("TMX".equals(item.category())) {
                Integer value = parseTemperature(item.fcstValue());
                if (value != null) {
                    maximums.add(value);
                }
            }
        }
        if (minimums.isEmpty() || maximums.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DailyTemperatures(
                minimums.stream().min(Integer::compareTo).orElseThrow(),
                maximums.stream().max(Integer::compareTo).orElseThrow()
        ));
    }

    private Optional<String> basisDateTime(List<Item> items) {
        return items.stream()
                .filter(item -> item != null && item.baseDate() != null && item.baseTime() != null)
                .map(item -> {
                    LocalDate baseDate = parseDate(item.baseDate());
                    LocalTime baseTime = parseTime(item.baseTime());
                    return baseDate == null || baseTime == null
                            ? null
                            : BASIS_DATE_TIME.format(LocalDateTime.of(baseDate, baseTime));
                })
                .filter(value -> value != null)
                .findFirst();
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, KMA_DATE);
        } catch (DateTimeParseException | NullPointerException exception) {
            return null;
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, KMA_TIME);
        } catch (DateTimeParseException | NullPointerException exception) {
            return null;
        }
    }

    private Double parseNumber(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim().replace("−", "-"));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        Double parsed = parseNumber(value);
        return parsed == null || parsed != Math.rint(parsed) ? null : parsed.intValue();
    }

    private Integer parseTemperature(String value) {
        Double parsed = parseNumber(value);
        if (parsed == null || !Double.isFinite(parsed)) {
            return null;
        }
        return (int) Math.round(parsed);
    }

    private BigDecimal parseExactPrecipitation(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replace(" ", "");
        if (normalized.equals("강수없음") || normalized.equals("없음")) {
            return BigDecimal.ZERO;
        }
        if (normalized.endsWith("mm") || normalized.endsWith("MM")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        try {
            BigDecimal amount = new BigDecimal(normalized);
            return amount.signum() < 0 ? null : amount;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String skyText(String skyCode) {
        return switch (skyCode) {
            case "1" -> "맑음";
            case "3" -> "구름많음";
            case "4" -> "흐림";
            default -> null;
        };
    }

    private String representativeSky(Map<String, Integer> skyCounts) {
        String representative = null;
        int highestCount = 0;
        for (Map.Entry<String, Integer> entry : skyCounts.entrySet()) {
            if (entry.getValue() > highestCount) {
                representative = entry.getKey();
                highestCount = entry.getValue();
            }
        }
        return representative;
    }

    private record OperatingWindow(LocalDateTime start, LocalDateTime end) {
    }

    private record ForecastHour(LocalTime time, Map<String, String> values) {
    }

    private record DailyTemperatures(int minimumC, int maximumC) {
    }
}
