package com.memme.controller.sales;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.exception.AuthenticationRequiredException;
import com.memme.dto.common.StatusResponse;
import com.memme.service.sales.analysis.SalesAnalysisQueryService;
import com.memme.service.sales.analysis.SalesAnalysisResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/v1/sales/analyses")
public class SalesAnalysisController {
    private final SalesAnalysisQueryService queryService;

    public SalesAnalysisController(SalesAnalysisQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public StatusResponse<?> getAnalysis(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false) AuthenticatedUserSession user,
            @RequestParam(defaultValue = "THIS_MONTH") String periodType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        if (user == null) throw new AuthenticationRequiredException();
        return switch (queryService.query(user.userId(), user.storeId(), periodType, startDate, endDate)) {
            case SalesAnalysisResult.Completed result ->
                    new StatusResponse<>("조회에 성공했습니다.", "COMPLETED", result.analysis());
            case SalesAnalysisResult.Empty result ->
                    new StatusResponse<>("선택 기간에 매출 데이터가 없습니다.", "EMPTY", result.analysis());
        };
    }
}
