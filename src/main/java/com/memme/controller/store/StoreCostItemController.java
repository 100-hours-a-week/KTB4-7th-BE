package com.memme.controller.store;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.ApiResponse;
import com.memme.dto.store.StoreCostItemGetData;
import com.memme.dto.store.StoreCostItemPutData;
import com.memme.dto.store.StoreCostItemPutResult;
import com.memme.dto.store.StoreCostItemRequest;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.store.StoreCostItemService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v2/stores/me/cost-items")
public class StoreCostItemController {

    private final StoreCostItemService costItemService;

    public StoreCostItemController(StoreCostItemService costItemService) {
        this.costItemService = costItemService;
    }

    @GetMapping("/{costMonth}")
    public ResponseEntity<ApiResponse<StoreCostItemGetData>> get(
            HttpServletRequest request, @PathVariable String costMonth) {
        AuthenticatedUserSession user = authenticatedUser(request);
        StoreCostItemGetData data = costItemService.get(user.userId(), user.storeId(), costMonth);
        String message = data.costItem() == null
                ? "저장된 순수익 분석 정보가 없습니다." : "조회에 성공했습니다.";
        return ResponseEntity.ok(new ApiResponse<>(message, data));
    }

    @PutMapping("/{costMonth}")
    public ResponseEntity<ApiResponse<StoreCostItemPutData>> put(
            HttpServletRequest request, @PathVariable String costMonth,
            @Valid @RequestBody StoreCostItemRequest body) {
        AuthenticatedUserSession user = authenticatedUser(request);
        StoreCostItemPutResult result = costItemService.put(user.userId(), user.storeId(), costMonth, body);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new ApiResponse<>("순수익 분석 정보가 저장되었습니다.", result.data()));
    }

    private AuthenticatedUserSession authenticatedUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new AuthenticationRequiredException();
        }
        Object attribute = session.getAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE);
        if (!(attribute instanceof AuthenticatedUserSession user)) {
            throw new AuthenticationRequiredException();
        }
        return user;
    }
}
