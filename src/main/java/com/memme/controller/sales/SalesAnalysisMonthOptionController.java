package com.memme.controller.sales;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.ApiResponse;
import com.memme.dto.sales.SalesAnalysisMonthOptionsResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.sales.analysis.SalesAnalysisMonthOptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/v2/sales/analyses/month-options")
public class SalesAnalysisMonthOptionController {

    private final SalesAnalysisMonthOptionService service;

    public SalesAnalysisMonthOptionController(SalesAnalysisMonthOptionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SalesAnalysisMonthOptionsResponse> get(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user) {
        if (user == null) {
            throw new AuthenticationRequiredException();
        }
        return new ApiResponse<>("조회에 성공했습니다.", service.get(user.userId(), user.storeId()));
    }
}
