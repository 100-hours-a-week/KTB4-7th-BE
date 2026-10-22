package com.memme.service.sales.upload;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.memme.dto.sales.SalesUploadHistoryRequest;
import com.memme.dto.sales.SalesUploadHistoryResponse;
import com.memme.dto.sales.SalesUploadStatusResponse;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesAnalysisEntity;
import com.memme.entity.sales.SalesAiInsightEntity;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.exception.sales.SalesUploadQueryException;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesAnalysisRepository;
import com.memme.repository.sales.SalesAiInsightRepository;
import com.memme.repository.sales.SalesOrderItemRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.memme.exception.sales.SalesUploadQueryException.Reason.INVALID_PAGE;
import static com.memme.exception.sales.SalesUploadQueryException.Reason.STORE_OWNER_REQUIRED;
import static com.memme.exception.sales.SalesUploadQueryException.Reason.UPLOAD_NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class SalesUploadQueryService {

    private static final int PAGE_SIZE = 10;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SalesUploadRepository uploadRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final SalesAiInsightRepository insightRepository;
    private final StoreOwnershipRepository storeOwnershipRepository;
    private final SalesAnalysisRepository salesAnalysisRepository;

    public SalesUploadQueryService(
            SalesUploadRepository uploadRepository,
            SalesOrderItemRepository salesOrderItemRepository,
            AnalysisRunRepository analysisRunRepository,
            SalesAiInsightRepository insightRepository,
            StoreOwnershipRepository storeOwnershipRepository,
            SalesAnalysisRepository salesAnalysisRepository
    ) {
        this.uploadRepository = uploadRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.insightRepository = insightRepository;
        this.storeOwnershipRepository = storeOwnershipRepository;
        this.salesAnalysisRepository = salesAnalysisRepository;
    }

    public SalesUploadHistoryResponse getHistory(
            Long userId,
            Long storeId,
            SalesUploadHistoryRequest request
    ) {
        validateOwner(userId, storeId);
        Objects.requireNonNull(request, "request");
        validatePage(request.page(), request.size());

        PageRequest pageable = PageRequest.of(request.page() - 1, PAGE_SIZE);
        Page<SalesUploadEntity> uploads = findUploads(storeId, request.targetMonth(), pageable);
        SalesUploadHistoryResponse.Connection connection = connection(storeId);
        List<SalesUploadHistoryResponse.Item> items = uploads.getContent().stream()
                .map(this::historyItem)
                .toList();

        return new SalesUploadHistoryResponse(
                connection,
                items,
                request.page(),
                PAGE_SIZE,
                uploads.getTotalPages(),
                uploads.getTotalElements()
        );
    }

    public SalesUploadStatusResponse getStatus(Long userId, Long storeId, Long uploadId) {
        validateOwner(userId, storeId);
        SalesUploadEntity upload = uploadRepository.findById(Objects.requireNonNull(uploadId, "uploadId"))
                .filter(found -> found.getStoreId().equals(storeId))
                .orElseThrow(() -> new SalesUploadQueryException(UPLOAD_NOT_FOUND));
        AnalysisRunEntity analysisRun = analysisRunRepository
                .findFirstByBasedOnUploadIdOrderByIdDesc(uploadId)
                .orElseThrow(() -> new SalesUploadQueryException(UPLOAD_NOT_FOUND));

        Optional<SalesAiInsightEntity> insight = upload.getPeriodEnd() == null
                ? Optional.empty()
                : insightRepository.findByStoreIdAndTargetMonth(
                        upload.getStoreId(), YearMonth.from(upload.getPeriodEnd()));
        SalesAiInsightStatus insightStatus = insight.map(SalesAiInsightEntity::getStatus)
                .orElse(SalesAiInsightStatus.PENDING);
        Long analysisId = findAnalysisId(uploadId, analysisRun, insight);
        boolean retryable = upload.getStatus() == SalesUploadStatus.COMPLETED
                && insightStatus == SalesAiInsightStatus.FAILED
                && analysisId != null
                && insight.map(found -> analysisId.equals(found.getSalesAnalysisId())).orElse(false);

        return new SalesUploadStatusResponse(
                retryable ? "기본 매출 분석은 완료되었지만 AI 인사이트 생성에 실패했습니다." : messageOf(upload.getStatus()),
                upload.getStatus().name(),
                retryable ? "AI_INSIGHT_ERROR" : upload.getFailReason(),
                new SalesUploadStatusResponse.Data(
                        upload.getId(),
                        analysisRun.getId(),
                        upload.getStatus() == SalesUploadStatus.COMPLETED || upload.getProcessingPhase() == null
                                ? null : upload.getProcessingPhase().name(),
                        analysisId,
                        insightStatus.name(),
                        retryable
                )
        );
    }

    private Long findAnalysisId(
            Long uploadId,
            AnalysisRunEntity currentRun,
            Optional<SalesAiInsightEntity> insight
    ) {
        Optional<SalesAnalysisEntity> currentAnalysis = salesAnalysisRepository.findByAnalysisRunId(currentRun.getId());
        if (currentAnalysis.isPresent()) {
            return currentAnalysis.get().getId();
        }
        return insight.map(SalesAiInsightEntity::getSalesAnalysisId)
                .flatMap(salesAnalysisRepository::findById)
                .filter(analysis -> analysisRunRepository.findById(analysis.getAnalysisRunId())
                        .map(run -> uploadId.equals(run.getBasedOnUploadId()))
                        .orElse(false))
                .map(SalesAnalysisEntity::getId)
                .orElse(null);
    }

    private Page<SalesUploadEntity> findUploads(
            Long storeId,
            YearMonth targetMonth,
            PageRequest pageable
    ) {
        if (targetMonth == null) {
            return uploadRepository.findByStoreIdOrderByUploadedAtDesc(storeId, pageable);
        }
        LocalDate start = targetMonth.atDay(1);
        LocalDate end = targetMonth.atEndOfMonth();
        return uploadRepository.findOverlappingPeriod(storeId, start, end, pageable);
    }

    private SalesUploadHistoryResponse.Connection connection(Long storeId) {
        SalesUploadEntity latest = uploadRepository.findFirstByStoreIdOrderByUploadedAtDesc(storeId)
                .orElse(null);
        long totalApplied = salesOrderItemRepository.countCurrentItemsByStoreId(storeId);
        return new SalesUploadHistoryResponse.Connection(
                latest == null ? null : toOffsetDateTime(latest),
                totalApplied,
                latest == null ? null : latest.getStatus().name()
        );
    }

    private SalesUploadHistoryResponse.Item historyItem(SalesUploadEntity upload) {
        return new SalesUploadHistoryResponse.Item(
                upload.getId(),
                upload.getOriginalFileName(),
                toOffsetDateTime(upload),
                zeroIfNull(upload.getTotalRowCount()),
                zeroIfNull(upload.getAppliedRecordCount()),
                upload.getStatus().name(),
                upload.getFailReason()
        );
    }

    private OffsetDateTime toOffsetDateTime(SalesUploadEntity upload) {
        return upload.getUploadedAt().atZone(SEOUL).toOffsetDateTime();
    }

    private String messageOf(SalesUploadStatus status) {
        return switch (status) {
            case PENDING -> "매출 파일 처리를 기다리고 있습니다.";
            case PROCESSING -> "매출 파일을 처리하고 있습니다.";
            case COMPLETED -> "매출 파일 처리가 완료되었습니다.";
            case FAILED -> "매출 파일 처리에 실패했습니다.";
        };
    }

    private long zeroIfNull(Long value) {
        return value == null ? 0 : value;
    }

    private void validateOwner(Long userId, Long storeId) {
        if (userId == null || storeId == null
                || !storeOwnershipRepository.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new SalesUploadQueryException(STORE_OWNER_REQUIRED);
        }
    }

    private void validatePage(Integer page, Integer size) {
        if (page == null || page < 1 || page > 5 || size == null || size != PAGE_SIZE) {
            throw new SalesUploadQueryException(INVALID_PAGE);
        }
    }
}
