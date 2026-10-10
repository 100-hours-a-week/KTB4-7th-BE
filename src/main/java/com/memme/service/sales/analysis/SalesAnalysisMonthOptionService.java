package com.memme.service.sales.analysis;

import static com.memme.exception.sales.SalesAnalysisRequestException.Reason.STORE_OWNER_REQUIRED;

import com.memme.dto.sales.SalesAnalysisMonthOptionsResponse;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.repository.sales.SalesOrderRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SalesAnalysisMonthOptionService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SalesUploadRepository uploadRepository;
    private final SalesOrderRepository orderRepository;
    private final StoreOwnershipRepository ownershipRepository;

    public SalesAnalysisMonthOptionService(SalesUploadRepository uploadRepository,
            SalesOrderRepository orderRepository, StoreOwnershipRepository ownershipRepository) {
        this.uploadRepository = uploadRepository;
        this.orderRepository = orderRepository;
        this.ownershipRepository = ownershipRepository;
    }

    public SalesAnalysisMonthOptionsResponse get(Long userId, Long storeId) {
        if (!ownershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SalesAnalysisRequestException(STORE_OWNER_REQUIRED);
        }
        Map<Long, Set<YearMonth>> orderMonths = new HashMap<>();
        for (Object[] row : orderRepository.findDistinctUploadMonths(storeId)) {
            Long uploadId = ((Number) row[0]).longValue();
            int year = ((Number) row[1]).intValue();
            int month = ((Number) row[2]).intValue();
            orderMonths.computeIfAbsent(uploadId, ignored -> new HashSet<>()).add(YearMonth.of(year, month));
        }

        Map<YearMonth, SalesAnalysisMonthOptionsResponse.MonthOption> options = new TreeMap<>();
        List<SalesUploadEntity> uploads = uploadRepository
                .findAllByStoreIdAndStatusOrderByUploadedAtDescIdDesc(storeId, SalesUploadStatus.COMPLETED);
        for (SalesUploadEntity upload : uploads) {
            Set<YearMonth> months = orderMonths.get(upload.getId());
            if (months == null || months.isEmpty()) {
                months = coverageMonths(upload);
            }
            for (YearMonth month : months) {
                options.putIfAbsent(month, new SalesAnalysisMonthOptionsResponse.MonthOption(
                        month.toString(), upload.getId(), upload.getOriginalFileName(),
                        upload.getUploadedAt().atZone(SEOUL).toOffsetDateTime()));
            }
        }
        return new SalesAnalysisMonthOptionsResponse(List.copyOf(options.values()));
    }

    private Set<YearMonth> coverageMonths(SalesUploadEntity upload) {
        Set<YearMonth> months = new HashSet<>();
        if (upload.getPeriodStart() == null || upload.getPeriodEnd() == null) {
            return months;
        }
        for (YearMonth month = YearMonth.from(upload.getPeriodStart());
                !month.isAfter(YearMonth.from(upload.getPeriodEnd())); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }
}
