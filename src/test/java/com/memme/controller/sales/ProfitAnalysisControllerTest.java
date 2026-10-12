package com.memme.controller.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.sales.ProfitAnalysisResponse;
import com.memme.dto.sales.ProfitMissingCostsResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.exception.sales.SalesAnalysisRequestException;
import com.memme.service.sales.profit.ProfitAnalysisQueryResult;
import com.memme.service.sales.profit.ProfitAnalysisQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfitAnalysisControllerTest {

    @Test
    void 완료_응답에는_요약과_일별_순이익을_반환한다() throws Exception {
        ProfitAnalysisQueryService service = mock(ProfitAnalysisQueryService.class);
        ProfitAnalysisResponse data = new ProfitAnalysisResponse(
                new ProfitAnalysisResponse.Summary(100, 40, 10, 50, 50,
                        new BigDecimal("0.5000"), null, null, null, null, null, null),
                List.of(new ProfitAnalysisResponse.DailyProfit(LocalDate.of(2026, 10, 7), 50)),
                List.of(new ProfitAnalysisResponse.WeekdayProfit("WEDNESDAY", 50)), null);
        when(service.query(1L, 2L, "TODAY", null, null))
                .thenReturn(new ProfitAnalysisQueryResult.Completed(data));
        var mvc = MockMvcBuilders.standaloneSetup(new ProfitAnalysisController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/v2/sales/profit-analyses").param("periodType", "TODAY").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.summary.netProfit").value(50))
                .andExpect(jsonPath("$.data.summary.totalCost").value(50))
                .andExpect(jsonPath("$.data.dailyProfits[0].netProfit").value(50))
                .andExpect(jsonPath("$.data.weekdayProfits[0].netProfit").value(50));
    }

    @Test
    void 비용_누락과_매출_없음을_구분한다() throws Exception {
        ProfitAnalysisQueryService service = mock(ProfitAnalysisQueryService.class);
        when(service.query(1L, 2L, "TODAY", null, null))
                .thenReturn(new ProfitAnalysisQueryResult.CostInputRequired(
                        new ProfitMissingCostsResponse(List.of("2026-10"))));
        when(service.query(1L, 2L, "THIS_WEEK", null, null))
                .thenReturn(new ProfitAnalysisQueryResult.Empty());
        var mvc = MockMvcBuilders.standaloneSetup(new ProfitAnalysisController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/v2/sales/profit-analyses").param("periodType", "TODAY").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COST_INPUT_REQUIRED"))
                .andExpect(jsonPath("$.data.missingCostMonths[0]").value("2026-10"));
        mvc.perform(get("/v2/sales/profit-analyses").param("periodType", "THIS_WEEK").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EMPTY"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 세션과_조회_기간_오류를_HTTP_상태로_반환한다() throws Exception {
        ProfitAnalysisQueryService service = mock(ProfitAnalysisQueryService.class);
        when(service.query(1L, 2L, "WRONG", null, null))
                .thenThrow(new SalesAnalysisRequestException(SalesAnalysisRequestException.Reason.INVALID_PERIOD));
        var mvc = MockMvcBuilders.standaloneSetup(new ProfitAnalysisController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/v2/sales/profit-analyses").param("periodType", "TODAY"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v2/sales/profit-analyses").param("periodType", "WRONG").session(session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("조회 기간이 올바르지 않습니다."));
    }

    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(1L, 2L));
        return session;
    }
}
