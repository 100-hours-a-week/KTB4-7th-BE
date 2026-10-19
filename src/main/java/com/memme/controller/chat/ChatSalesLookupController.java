package com.memme.controller.chat;

import com.memme.dto.chat.ChatDistrictBenchmarkResponse;
import com.memme.dto.chat.ChatLookupResponse;
import com.memme.dto.chat.ChatWeatherResponse;
import com.memme.service.chat.ChatSalesLookupService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v2/sales")
public class ChatSalesLookupController {

    private final ChatSalesLookupService lookupService;

    public ChatSalesLookupController(ChatSalesLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping("/weather")
    public ChatLookupResponse<ChatWeatherResponse> weather(
            @RequestParam Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        return lookupService.weather(storeId, targetDate);
    }

    @GetMapping("/district-benchmark")
    public ChatLookupResponse<ChatDistrictBenchmarkResponse> districtBenchmark(@RequestParam Long storeId) {
        return lookupService.districtBenchmark(storeId);
    }
}
