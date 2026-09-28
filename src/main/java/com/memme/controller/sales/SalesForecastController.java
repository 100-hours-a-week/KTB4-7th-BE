package com.memme.controller.sales;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.StatusResponse;
import com.memme.dto.sales.SalesExpectedForecastResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.sales.forecast.SalesExpectedForecastQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/v1/sales/forecasts")
public class SalesForecastController {
    private final SalesExpectedForecastQueryService queryService;

    public SalesForecastController(SalesExpectedForecastQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/expected")
    public StatusResponse<SalesExpectedForecastResponse> getExpectedForecast(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user
    ) {
        if (user == null) throw new AuthenticationRequiredException();
        return queryService.query(user.userId(), user.storeId())
                .map(response -> new StatusResponse<>("예상 매출을 조회했습니다.", "COMPLETED", response))
                .orElseGet(() -> new StatusResponse<>("예측 매출 데이터가 없습니다.", "EMPTY", null));
    }
}
