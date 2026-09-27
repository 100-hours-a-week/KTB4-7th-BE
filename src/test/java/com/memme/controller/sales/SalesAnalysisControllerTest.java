package com.memme.controller.sales;

import java.util.List;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.sales.SalesAvailableMonthsResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.sales.analysis.SalesAnalysisQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalesAnalysisControllerTest {

    private SalesAnalysisQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(SalesAnalysisQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SalesAnalysisController(queryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsAvailableMonthsForAuthenticatedStoreOwner() throws Exception {
        when(queryService.getAvailableMonths(7L, 301L)).thenReturn(
                new SalesAvailableMonthsResponse(List.of("2026-03", "2026-04"))
        );

        mockMvc.perform(get("/v1/sales/analyses/months")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(7L, 301L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[0]").value("2026-03"))
                .andExpect(jsonPath("$.months[1]").value("2026-04"));

        verify(queryService).getAvailableMonths(7L, 301L);
    }

    @Test
    void rejectsAvailableMonthsWithoutSession() throws Exception {
        mockMvc.perform(get("/v1/sales/analyses/months"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }
}
