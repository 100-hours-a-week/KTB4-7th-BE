package com.memme.controller.sales;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.sales.SalesAnalysisMonthOptionsResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.sales.analysis.SalesAnalysisMonthOptionService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SalesAnalysisMonthOptionControllerTest {

    @Test
    void 완료된_업로드의_월과_파일명을_반환한다() throws Exception {
        SalesAnalysisMonthOptionService service = mock(SalesAnalysisMonthOptionService.class);
        when(service.get(1L, 2L)).thenReturn(new SalesAnalysisMonthOptionsResponse(List.of(
                new SalesAnalysisMonthOptionsResponse.MonthOption("2026-08", 3L, "매출.xlsx",
                        OffsetDateTime.parse("2026-10-03T12:00:00+09:00")))));
        var mvc = MockMvcBuilders.standaloneSetup(new SalesAnalysisMonthOptionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(1L, 2L));

        mvc.perform(get("/v2/sales/analyses/month-options").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.months[0].targetMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.months[0].fileName").value("매출.xlsx"));
        mvc.perform(get("/v2/sales/analyses/month-options"))
                .andExpect(status().isUnauthorized());
    }
}
