package com.memme.controller.sales;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.FieldError;
import com.memme.dto.sales.SalesCalendarResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.exception.sales.SalesCalendarInputException;
import com.memme.exception.store.StoreProfileNotFoundException;
import com.memme.service.sales.calendar.SalesCalendarQueryService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class SalesCalendarControllerTest {
    private final SalesCalendarQueryService service = mock(SalesCalendarQueryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SalesCalendarController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void sendsSessionUserIdAndIgnoresRequestedStoreId() throws Exception {
        var date = LocalDate.of(2026, 10, 28);
        when(service.query(7L, "2026-10", "2026-10-28")).thenReturn(new SalesCalendarResponse(
                "2026-10", "Asia/Seoul", new SalesCalendarResponse.AvailableRange("2026-01", "2026-10"),
                date, new SalesCalendarResponse.MonthSummary(0, 0, null, 0), List.of(),
                new SalesCalendarResponse.SelectedDay(date, "UNKNOWN", null, null, null,
                        new SalesCalendarResponse.Insight("UNAVAILABLE", null, null))));
        mvc.perform(get("/v3/sales/calendar").param("month", "2026-10").param("selectedDate", "2026-10-28")
                        .param("storeId", "999").sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(7L, 301L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.month").value("2026-10"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"));
        verify(service).query(7L, "2026-10", "2026-10-28");
    }

    @Test
    void requiresSession() throws Exception {
        mvc.perform(get("/v3/sales/calendar")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void returnsFieldErrorsForMalformedQueries() throws Exception {
        when(service.query(7L, "bad", null)).thenThrow(new SalesCalendarInputException(
                List.of(new FieldError("month", "조회 월이 올바르지 않습니다."))));
        mvc.perform(get("/v3/sales/calendar").param("month", "bad")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(7L, 301L)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.fieldErrors[0].field").value("month"));
    }

    @Test
    void mapsRangeAndMissingStoreErrors() throws Exception {
        when(service.query(7L, null, null)).thenThrow(new ResponseStatusException(
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "조회 가능한 매출 기간을 벗어났습니다."));
        mvc.perform(get("/v3/sales/calendar").sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                        new AuthenticatedUserSession(7L, 301L))).andExpect(status().isRequestedRangeNotSatisfiable());
        doThrow(new StoreProfileNotFoundException()).when(service).query(7L, null, null);
        mvc.perform(get("/v3/sales/calendar").sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                        new AuthenticatedUserSession(7L, 301L))).andExpect(status().isNotFound());
    }
}
