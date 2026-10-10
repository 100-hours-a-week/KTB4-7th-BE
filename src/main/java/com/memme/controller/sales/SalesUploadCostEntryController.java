package com.memme.controller.sales;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.ApiResponse;
import com.memme.dto.sales.SalesUploadCostEntryResponse;
import com.memme.dto.sales.SalesUploadCostSaveRequest;
import com.memme.dto.sales.SalesUploadCostSaveResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.sales.profit.SalesUploadCostEntryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/v2/sales/uploads/{uploadId}/cost-items")
public class SalesUploadCostEntryController {

    private final SalesUploadCostEntryService service;

    public SalesUploadCostEntryController(SalesUploadCostEntryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SalesUploadCostEntryResponse> get(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user, @PathVariable Long uploadId) {
        AuthenticatedUserSession authenticated = authenticated(user);
        return new ApiResponse<>("조회에 성공했습니다.",
                service.get(authenticated.userId(), authenticated.storeId(), uploadId));
    }

    @PutMapping
    public ApiResponse<SalesUploadCostSaveResponse> put(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user, @PathVariable Long uploadId,
            @Valid @RequestBody SalesUploadCostSaveRequest body) {
        AuthenticatedUserSession authenticated = authenticated(user);
        return new ApiResponse<>("순수익 분석 정보가 저장되었습니다.",
                service.put(authenticated.userId(), authenticated.storeId(), uploadId, body));
    }

    private AuthenticatedUserSession authenticated(AuthenticatedUserSession user) {
        if (user == null) {
            throw new AuthenticationRequiredException();
        }
        return user;
    }
}
