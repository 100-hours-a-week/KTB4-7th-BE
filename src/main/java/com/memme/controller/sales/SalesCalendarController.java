package com.memme.controller.sales;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.StatusResponse;
import com.memme.dto.sales.SalesCalendarResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.sales.calendar.SalesCalendarQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/v3/sales/calendar")
public class SalesCalendarController {

    private final SalesCalendarQueryService queryService;

    public SalesCalendarController(SalesCalendarQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public StatusResponse<SalesCalendarResponse> getCalendar(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false)
            AuthenticatedUserSession user,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String selectedDate
    ) {
        if (user == null) {
            throw new AuthenticationRequiredException();
        }
        return new StatusResponse<>(
                "매출 달력을 조회했습니다.",
                "COMPLETED",
                queryService.query(user.userId(), month, selectedDate)
        );
    }

    @GetMapping("/days/{date}")
    public StatusResponse<SalesCalendarResponse.SelectedDay> getDay(
            @SessionAttribute(value = AuthenticatedUserSession.SESSION_ATTRIBUTE, required = false) AuthenticatedUserSession user,
            @PathVariable String date) {
        if (user == null) throw new AuthenticationRequiredException();
        return new StatusResponse<>("일별 매출 정보를 조회했습니다.", "COMPLETED", queryService.queryDay(user.userId(), date));
    }
}
