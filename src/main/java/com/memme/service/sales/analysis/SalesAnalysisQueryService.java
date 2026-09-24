package com.memme.service.sales.analysis;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import com.memme.dto.sales.SalesPeriod;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.repository.store.StoreOwnershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.memme.exception.sales.SalesAnalysisRequestException.Reason.INVALID_PERIOD;
import static com.memme.exception.sales.SalesAnalysisRequestException.Reason.STORE_OWNER_REQUIRED;

@Service
@Transactional(readOnly = true)
public class SalesAnalysisQueryService {
    private final SalesAnalysisService analysisService;
    private final StoreOwnershipRepository ownershipRepository;
    private final Clock clock;

    public SalesAnalysisQueryService(SalesAnalysisService analysisService,
                                     StoreOwnershipRepository ownershipRepository, Clock clock) {
        this.analysisService = analysisService;
        this.ownershipRepository = ownershipRepository;
        this.clock = clock;
    }

    public SalesAnalysisResult query(Long userId, Long storeId, String periodType,
                                     String startDate, String endDate) {
        if (!ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SalesAnalysisRequestException(STORE_OWNER_REQUIRED);
        }
        SalesPeriod period = resolvePeriod(periodType, startDate, endDate);
        return analysisService.analyze(storeId, period.type(), period.startDate(), period.endDate());
    }

    SalesPeriod resolvePeriod(String type, String startDate, String endDate) {
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
