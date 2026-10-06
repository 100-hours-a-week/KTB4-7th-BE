package com.memme.controller.sales;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.StatusResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.sales.profit.ProfitAnalysisQueryResult;
import com.memme.service.sales.profit.ProfitAnalysisQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/v2/sales/profit-analyses")
public class ProfitAnalysisController {

    private final ProfitAnalysisQueryService queryService;

    public ProfitAnalysisController(ProfitAnalysisQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public StatusResponse<?> getAnalysis(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user,
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        if (user == null) {
            throw new AuthenticationRequiredException();
        }
        return switch (queryService.query(user.userId(), user.storeId(), periodType, startDate, endDate)) {
            case ProfitAnalysisQueryResult.Completed result ->
                    new StatusResponse<>("조회에 성공했습니다.", "COMPLETED", result.data());
            case ProfitAnalysisQueryResult.CostInputRequired result ->
                    new StatusResponse<>("순수익 분석 정보를 입력해주세요.", "COST_INPUT_REQUIRED", result.data());
            case ProfitAnalysisQueryResult.Empty ignored ->
                    new StatusResponse<>("선택 기간에 매출 데이터가 없습니다.", "EMPTY", null);
        };
    }
}
