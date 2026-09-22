package com.memme.service.sales.upload;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import com.memme.dto.sales.SalesUploadHistoryRequest;
import com.memme.dto.sales.SalesUploadHistoryResponse;
import com.memme.dto.sales.SalesUploadStatusResponse;
import com.memme.entity.sales.AnalysisRunEntity;
import com.memme.entity.sales.SalesUploadEntity;
import com.memme.entity.sales.SalesUploadProcessingPhase;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.exception.SalesUploadQueryException;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.memme.exception.SalesUploadQueryException.Reason.INVALID_PAGE;
import static com.memme.exception.SalesUploadQueryException.Reason.STORE_OWNER_REQUIRED;
import static com.memme.exception.SalesUploadQueryException.Reason.UPLOAD_NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class SalesUploadQueryService {

    private static final int PAGE_SIZE = 10;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SalesUploadRepository uploadRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final StoreOwnershipRepository storeOwnershipRepository;

    public SalesUploadQueryService(
            SalesUploadRepository uploadRepository,
            AnalysisRunRepository analysisRunRepository,
            StoreOwnershipRepository storeOwnershipRepository
    ) {
        this.uploadRepository = uploadRepository;
        this.analysisRunRepository = analysisRunRepository;
        this.storeOwnershipRepository = storeOwnershipRepository;
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
        AnalysisRunEntity analysisRun = analysisRunRepository.findByBasedOnUploadId(uploadId)
                .orElseThrow(() -> new SalesUploadQueryException(UPLOAD_NOT_FOUND));

        return new SalesUploadStatusResponse(
                messageOf(upload.getStatus()),
                upload.getStatus().name(),
                upload.getFailReason(),
                new SalesUploadStatusResponse.Data(
                        upload.getId(),
                        analysisRun.getId(),
                        progressOf(upload),
                        null,
                        upload.getStatus() == SalesUploadStatus.FAILED
                )
        );
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
        long totalApplied = uploadRepository.sumAppliedRecordCount(storeId, SalesUploadStatus.COMPLETED);
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

    private SalesUploadStatusResponse.Progress progressOf(SalesUploadEntity upload) {
        return switch (upload.getStatus()) {
            case PENDING -> new SalesUploadStatusResponse.Progress("PENDING", 0);
            case COMPLETED -> new SalesUploadStatusResponse.Progress("COMPLETED", 100);
            case FAILED -> new SalesUploadStatusResponse.Progress("FAILED", phasePercent(upload.getProcessingPhase()));
            case PROCESSING -> new SalesUploadStatusResponse.Progress(
                    upload.getProcessingPhase() == null ? "PROCESSING" : upload.getProcessingPhase().name(),
                    phasePercent(upload.getProcessingPhase())
            );
        };
    }

    private int phasePercent(SalesUploadProcessingPhase phase) {
        if (phase == null) {
            return 0;
        }
        return switch (phase) {
            case VALIDATING -> 20;
            case NORMALIZING -> 40;
            case AGGREGATING -> 70;
            case ANALYZING -> 90;
        };
    }

    private String messageOf(SalesUploadStatus status) {
        return switch (status) {
            case PENDING -> "매출 파일 업로드를 기다리고 있습니다.";
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
