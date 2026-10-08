package com.memme.controller.store;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.ApiResponse;
import com.memme.dto.common.StatusResponse;
import com.memme.dto.store.MenuBatchResponse;
import com.memme.dto.store.MenuConfirmationRequest;
import com.memme.dto.store.MenuConfirmationResponse;
import com.memme.dto.store.MenuListResponse;
import com.memme.dto.store.MenuImageUploadResponse;
import com.memme.dto.store.MenuPatchRequest;
import com.memme.dto.store.MenuPatchResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.exception.store.MenuRequestException;
import com.memme.service.store.MenuQueryService;
import com.memme.service.store.MenuWriteService;
import com.memme.service.store.MenuUploadService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v2")
public class MenuController {

    private final MenuQueryService queryService;
    private final MenuWriteService writeService;
    private final MenuUploadService uploadService;

    public MenuController(MenuQueryService queryService, MenuWriteService writeService,
            MenuUploadService uploadService) {
        this.queryService = queryService;
        this.writeService = writeService;
        this.uploadService = uploadService;
    }

    @PostMapping(value = "/menu-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StatusResponse<MenuImageUploadResponse> uploadImages(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession session,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        requireAuthentication(session);
        return new StatusResponse<>("메뉴판 이미지를 접수하고 AI 분석을 시작했습니다.", "PENDING",
                uploadService.upload(session.userId(), session.storeId(), idempotencyKey, images));
    }

    @GetMapping("/menu-items")
    public ApiResponse<MenuListResponse> getMenuItems(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession session) {
        requireAuthentication(session);
        return new ApiResponse<>("조회에 성공했습니다.",
                queryService.getMenuList(session.userId(), session.storeId()));
    }

    @PatchMapping("/menu-items")
    public ApiResponse<MenuPatchResponse> patchMenuItems(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession session,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MenuPatchRequest request) {
        requireAuthentication(session);
        return new ApiResponse<>("메뉴 정보가 저장되었습니다.",
                writeService.patch(session.userId(), session.storeId(), idempotencyKey, request));
    }

    @GetMapping("/menu-image-batches/{batchId}")
    public StatusResponse<MenuBatchResponse> getBatch(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession session,
            @PathVariable long batchId) {
        requireAuthentication(session);
        MenuQueryService.BatchResult result = queryService.getBatch(session.userId(), session.storeId(), batchId);
        String message = switch (result.status()) {
            case "PROCESSING", "PENDING" -> "AI가 메뉴 정보를 분석하고 있어요.";
            case "FAILED" -> "메뉴 정보를 불러오지 못했습니다. 다시 업로드해주세요.";
            default -> result.data().detectedItems().isEmpty()
                    ? "추출된 메뉴 정보가 없습니다. 메뉴판 이미지를 다시 업로드해주세요."
                    : "메뉴 인식이 완료되었습니다.";
        };
        return new StatusResponse<>(message, result.status(), result.data());
    }

    @PostMapping("/menu-image-batches/{batchId}/confirmations")
    public ApiResponse<MenuConfirmationResponse> confirmBatch(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession session,
            @PathVariable long batchId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MenuConfirmationRequest request) {
        requireAuthentication(session);
        return new ApiResponse<>("메뉴 정보가 저장되었습니다.",
                writeService.confirm(session.userId(), session.storeId(), batchId, idempotencyKey, request));
    }

    @ExceptionHandler(MenuRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleMenuRequest(MenuRequestException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiResponse<>(exception.getMessage(), exception.getData()));
    }

    private void requireAuthentication(AuthenticatedUserSession session) {
        if (session == null) {
            throw new AuthenticationRequiredException();
        }
    }
}
