package com.memme.controller.sales;

import java.io.IOException;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.exception.AuthenticationRequiredException;
import com.memme.dto.common.StatusResponse;
import com.memme.dto.sales.SalesUploadHistoryRequest;
import com.memme.dto.sales.SalesUploadHistoryResponse;
import com.memme.dto.sales.SalesUploadRequest;
import com.memme.dto.sales.SalesUploadResponse;
import com.memme.dto.sales.SalesUploadStatusResponse;
import com.memme.exception.SalesUploadProcessingException;
import com.memme.exception.SalesUploadRequestException;
import com.memme.service.sales.upload.SalesUploadCommand;
import com.memme.service.sales.upload.SalesUploadQueryService;
import com.memme.service.sales.upload.SalesUploadReceipt;
import com.memme.service.sales.upload.SalesUploadService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/v1/sales/uploads")
public class SalesUploadController {

    private final SalesUploadService salesUploadService;
    private final SalesUploadQueryService salesUploadQueryService;

    public SalesUploadController(
            SalesUploadService salesUploadService,
            SalesUploadQueryService salesUploadQueryService
    ) {
        this.salesUploadService = salesUploadService;
        this.salesUploadQueryService = salesUploadQueryService;
    }

    @GetMapping
    public SalesUploadHistoryResponse getHistory(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession authenticatedUser,
            @Valid @ModelAttribute SalesUploadHistoryRequest request
    ) {
        requireAuthentication(authenticatedUser);
        return salesUploadQueryService.getHistory(
                authenticatedUser.userId(),
                authenticatedUser.storeId(),
                request
        );
    }

    @GetMapping("/{uploadId}")
    public SalesUploadStatusResponse getStatus(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession authenticatedUser,
            @PathVariable Long uploadId
    ) {
        requireAuthentication(authenticatedUser);
        return salesUploadQueryService.getStatus(
                authenticatedUser.userId(),
                authenticatedUser.storeId(),
                uploadId
        );
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StatusResponse<SalesUploadResponse>> upload(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession authenticatedUser,
            @ModelAttribute SalesUploadRequest request,
            MultipartHttpServletRequest multipartRequest
    ) {
        requireAuthentication(authenticatedUser);
        validateFileCount(multipartRequest);
        SalesUploadReceipt receipt = salesUploadService.upload(toCommand(authenticatedUser, request));
        return ResponseEntity.status(HttpStatus.OK).body(new StatusResponse<>(
                "매출 파일 업로드가 완료되었습니다.",
                receipt.status(),
                new SalesUploadResponse(receipt.uploadId(), receipt.analysisRunId())
        ));
    }

    private void requireAuthentication(AuthenticatedUserSession authenticatedUser) {
        if (authenticatedUser == null) {
            throw new AuthenticationRequiredException();
        }
    }

    private void validateFileCount(MultipartHttpServletRequest request) {
        if (request.getFiles("file").size() != 1) {
            throw new SalesUploadRequestException(
                    "INVALID_FILE_COUNT",
                    "매출 파일은 한 개만 업로드할 수 있습니다."
            );
        }
    }

    private SalesUploadCommand toCommand(
            AuthenticatedUserSession authenticatedUser,
            SalesUploadRequest request
    ) {
        if (request == null || request.file() == null) {
            throw new SalesUploadRequestException("EMPTY_FILE", "업로드할 파일을 선택해 주세요.");
        }

        String originalFileName = request.file().getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new SalesUploadRequestException("INVALID_FILE_NAME", "파일 이름을 확인해 주세요.");
        }

        return new SalesUploadCommand(
                authenticatedUser.storeId(),
                authenticatedUser.userId(),
                originalFileName,
                readContent(request)
        );
    }

    private byte[] readContent(SalesUploadRequest request) {
        try {
            return request.file().getBytes();
        } catch (IOException exception) {
            throw new SalesUploadProcessingException("매출 파일을 읽지 못했습니다.", exception);
        }
    }
}
