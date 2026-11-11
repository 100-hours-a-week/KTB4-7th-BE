package com.memme.service.sales.analysis;

import java.time.Clock;
import java.time.Duration;
import com.memme.service.cache.SharedJsonCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.memme.dto.sales.SalesAvailableMonthsResponse;
import com.memme.dto.sales.SalesPeriod;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.memme.exception.sales.SalesAnalysisRequestException.Reason.INVALID_PERIOD;
import static com.memme.exception.sales.SalesAnalysisRequestException.Reason.STORE_OWNER_REQUIRED;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SalesAnalysisQueryService {
    static final long CACHE_TTL_MIN_SECONDS = 240;
    static final long CACHE_TTL_MAX_SECONDS = 360;
    static final long IN_FLIGHT_WAIT_MILLIS = 5_000;
    private final ConcurrentHashMap<String, CompletableFuture<SalesAnalysisResult>> inFlight =
            new ConcurrentHashMap<>();
    private final SalesAnalysisService analysisService;
    private final SalesDailySummaryRepository dailySummaryRepository;
    private final StoreOwnershipRepository ownershipRepository;
    private final Clock clock;
    private final SharedJsonCache cache;
    private final SalesAnalysisCacheVersion cacheVersion;

    public SalesAnalysisQueryService(SalesAnalysisService analysisService,
                                     SalesDailySummaryRepository dailySummaryRepository,
                                     StoreOwnershipRepository ownershipRepository, Clock clock) {
        this(analysisService, dailySummaryRepository, ownershipRepository, clock, null, null);
    }

    @Autowired
    public SalesAnalysisQueryService(SalesAnalysisService analysisService,
            SalesDailySummaryRepository dailySummaryRepository, StoreOwnershipRepository ownershipRepository,
            Clock clock, SharedJsonCache cache, SalesAnalysisCacheVersion cacheVersion) {
        this.cache = cache;
        this.cacheVersion = cacheVersion;
        this.analysisService = analysisService;
        this.dailySummaryRepository = dailySummaryRepository;
        this.ownershipRepository = ownershipRepository;
        this.clock = clock;
    }

    public SalesAvailableMonthsResponse getAvailableMonths(Long userId, Long storeId) {
        if (!ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SalesAnalysisRequestException(STORE_OWNER_REQUIRED);
        }
        List<String> months = dailySummaryRepository.findAllByStoreIdOrderBySalesDateAsc(storeId).stream()
                .map(summary -> YearMonth.from(summary.getSalesDate()).toString())
                .distinct()
                .toList();
        return new SalesAvailableMonthsResponse(months);
    }

    public SalesAnalysisResult query(Long userId, Long storeId, String periodType,
                                     String startDate, String endDate) {
        if (!ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SalesAnalysisRequestException(STORE_OWNER_REQUIRED);
        }
        SalesPeriod period = resolvePeriod(periodType, startDate, endDate);
        if (cache == null || !cache.isEnabled()) {
            return analysisService.analyze(storeId, period.type(), period.startDate(), period.endDate());
        }
        String version = cacheVersion.read(storeId, period.startDate(), period.endDate());
        String key = "sales:analysis:v1:" + storeId + ":" + period.type() + ":"
                + period.startDate() + ":" + period.endDate() + ":" + version;
        var hit = readCached(key);
        if (hit != null) return hit;
        CompletableFuture<SalesAnalysisResult> mine = new CompletableFuture<>();
        CompletableFuture<SalesAnalysisResult> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            SalesAnalysisResult shared = awaitInFlight(running);
            if (shared != null) return shared;
            return analyzeAndCache(key, storeId, period);
        }
        try {
            SalesAnalysisResult result = readCached(key);
            if (result == null) result = analyzeAndCache(key, storeId, period);
            mine.complete(result);
            return result;
        } catch (RuntimeException | Error exception) {
            mine.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private SalesAnalysisResult.Completed readCached(String key) {
        var hit = cache.get(key, SalesAnalysisResult.Completed.class);
        if (hit.isPresent() && hit.get().analysis() != null && hit.get().categories() != null) return hit.get();
        return null;
    }

    private SalesAnalysisResult analyzeAndCache(String key, Long storeId, SalesPeriod period) {
        SalesAnalysisResult result = analysisService.analyze(storeId, period.type(), period.startDate(), period.endDate());
        if (result instanceof SalesAnalysisResult.Completed completed) {
            cache.put(key, completed, cacheTtl());
        }
        return result;
    }

    // 같은 키를 이미 계산 중인 요청이 있으면 그 결과를 함께 쓴다. 실패하거나 오래 걸리면 직접 계산한다.
    private SalesAnalysisResult awaitInFlight(CompletableFuture<SalesAnalysisResult> running) {
        try {
            return running.get(IN_FLIGHT_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException exception) {
            return null;
        }
    }

    // 키들이 같은 순간에 만료되지 않도록 만료 시간을 4~6분 사이로 흩는다.
    static Duration cacheTtl() {
        return Duration.ofSeconds(ThreadLocalRandom.current()
                .nextLong(CACHE_TTL_MIN_SECONDS, CACHE_TTL_MAX_SECONDS + 1));
    }

    public SalesPeriod resolvePeriod(String type, String startDate, String endDate) {
        LocalDate today = LocalDate.now(clock);
        if (type == null || (!"CUSTOM".equals(type) && (startDate != null || endDate != null))) {
            throw new SalesAnalysisRequestException(INVALID_PERIOD);
        }
        try {
            return switch (type) {
                case "TODAY" -> new SalesPeriod(type, today, today);
                case "THIS_WEEK" -> new SalesPeriod(type,
                        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), today);
                case "THIS_MONTH" -> new SalesPeriod(type, today.withDayOfMonth(1), today);
                case "CUSTOM" -> {
                    if (startDate == null || endDate == null
                            || !startDate.matches("\\d{4}-\\d{2}-\\d{2}")
                            || !endDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        throw new SalesAnalysisRequestException(INVALID_PERIOD);
                    }
                    LocalDate start = LocalDate.parse(startDate);
                    LocalDate end = LocalDate.parse(endDate);
                    if (start.isAfter(end)) throw new SalesAnalysisRequestException(INVALID_PERIOD);
                    yield new SalesPeriod(type, start, end);
                }
                default -> throw new SalesAnalysisRequestException(INVALID_PERIOD);
            };
        } catch (DateTimeException exception) {
            throw new SalesAnalysisRequestException(INVALID_PERIOD);
        }
    }
}
