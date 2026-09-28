package com.memme.controller.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.sales.SalesExpectedForecastResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.sales.forecast.SalesExpectedForecastQueryService;
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

class SalesForecastControllerTest {
    private SalesExpectedForecastQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(SalesExpectedForecastQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SalesForecastController(queryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsExpectedSalesForAuthenticatedStoreOwner() throws Exception {
        when(queryService.query(7L, 301L)).thenReturn(Optional.of(new SalesExpectedForecastResponse(
                YearMonth.of(2026, 9),
                BigDecimal.ZERO,
                BigDecimal.valueOf(1_000),
                BigDecimal.valueOf(1_000),
                BigDecimal.valueOf(850),
                BigDecimal.valueOf(1_150),
                List.of(new SalesExpectedForecastResponse.DailyForecast(
                        LocalDate.of(2026, 9, 1),
                        BigDecimal.valueOf(1_000),
                        BigDecimal.valueOf(850),
                        BigDecimal.valueOf(1_150)
                ))
        )));

        mockMvc.perform(get("/v1/sales/forecasts/expected")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(7L, 301L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.targetMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.expectedSalesAmount").value(1000));

        verify(queryService).query(7L, 301L);
    }

    @Test
    void returnsEmptyWhenForecastDoesNotExist() throws Exception {
        when(queryService.query(7L, 301L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/sales/forecasts/expected")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(7L, 301L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EMPTY"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void rejectsExpectedForecastWithoutSession() throws Exception {
        mockMvc.perform(get("/v1/sales/forecasts/expected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }
}
