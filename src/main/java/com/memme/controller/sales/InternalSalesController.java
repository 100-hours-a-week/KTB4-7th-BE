package com.memme.controller.sales;

import java.time.LocalDate;

import com.memme.dto.common.ApiResponse;
import com.memme.dto.sales.InternalSalesCategoriesResponse;
import com.memme.dto.sales.SalesForecastsResponse;
import com.memme.dto.sales.InternalSalesHourlyProfilesResponse;
import com.memme.dto.sales.InternalSalesSummaryResponse;
import com.memme.service.sales.analysis.InternalSalesQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/sales")
public class InternalSalesController {

    private final InternalSalesQueryService queryService;

    public InternalSalesController(InternalSalesQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/summary")
    public ApiResponse<InternalSalesSummaryResponse> summary(
            @RequestParam Long storeId,
            @RequestParam String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return new ApiResponse<>(
                "조회에 성공했습니다.",
                queryService.summary(storeId, period, startDate, endDate)
        );
    }

    @GetMapping("/categories")
    public ApiResponse<InternalSalesCategoriesResponse> categories(
            @RequestParam Long storeId,
            @RequestParam String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return new ApiResponse<>(
                "조회에 성공했습니다.",
                queryService.categories(storeId, period, startDate, endDate)
        );
    }

    @GetMapping("/hourly-profiles")
    public ApiResponse<InternalSalesHourlyProfilesResponse> hourlyProfiles(
            @RequestParam Long storeId,
            @RequestParam String period,
            @RequestParam(required = false) String dayOfWeek,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return new ApiResponse<>(
                "조회에 성공했습니다.",
                queryService.hourlyProfiles(
                        storeId,
                        period,
                        dayOfWeek,
                        startDate,
                        endDate
                )
        );
    }

    @GetMapping("/forecasts")
    public ApiResponse<SalesForecastsResponse> forecasts(
            @RequestParam Long storeId,
            @RequestParam(required = false) LocalDate targetDate
    ) {
        return new ApiResponse<>(
                "조회에 성공했습니다.",
                queryService.forecasts(storeId, targetDate)
        );
    }
}
