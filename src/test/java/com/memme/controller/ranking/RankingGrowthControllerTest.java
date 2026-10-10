package com.memme.controller.ranking;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.ranking.RankingGrowthResponse;
import com.memme.dto.ranking.RankingPeriod;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.ranking.RankingGrowthQueryService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RankingGrowthControllerTest {

    private RankingGrowthQueryService queryService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        queryService = mock(RankingGrowthQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RankingGrowthController(queryService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsPreviousMonthRankingForSessionStore() throws Exception {
        when(queryService.query(7L, 301L)).thenReturn(new RankingGrowthResponse(
                "조회에 성공했습니다.", RankingGrowthResponse.Status.COMPLETED,
                new RankingGrowthResponse.Data(true,
                        new RankingGrowthResponse.Period(RankingPeriod.LAST_MONTH,
                                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null),
                        List.of(), null,
                        new RankingGrowthResponse.MyEligibility(
                                RankingGrowthResponse.EligibilityStatus.UNKNOWN, null))));

        mvc.perform(get("/v2/rankings/growth")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(7L, 301L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.period.type").value("LAST_MONTH"));
    }

    @Test
    void rejectsMissingSession() throws Exception {
        mvc.perform(get("/v2/rankings/growth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    void rejectsUnsupportedPeriodParameter() throws Exception {
        mvc.perform(get("/v2/rankings/growth?period=THIS_MONTH")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(7L, 301L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 조회 조건입니다."));
    }

    @Test
    void returnsRankingServerErrorWhenQueryFails() throws Exception {
        when(queryService.query(7L, 301L))
                .thenThrow(new IllegalStateException("db unavailable"));

        mvc.perform(get("/v2/rankings/growth")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(7L, 301L)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("성장 랭킹을 불러오지 못했습니다. 잠시 후 다시 시도해주세요."));
    }
}
