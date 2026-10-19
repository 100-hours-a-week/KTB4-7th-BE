package com.memme.controller.chat;

import com.memme.config.ChatSalesLookupAuthenticationFilter;
import com.memme.dto.chat.ChatLookupResponse;
import com.memme.dto.chat.ChatWeatherResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.chat.ChatSalesLookupService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatSalesLookupControllerTest {

    private ChatSalesLookupService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ChatSalesLookupService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ChatSalesLookupController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new ChatSalesLookupAuthenticationFilter("test-token"))
                .build();
    }

    @Test
    void returnsReadyWeatherForRequestedDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 19);
        when(service.weather(1L, date)).thenReturn(ChatLookupResponse.ready(
                new ChatWeatherResponse("2026-10-19 11:00", "비", 12, 19,
                        0.6, List.of(17), null, true)));

        mvc.perform(get("/internal/v2/sales/weather").header("Authorization", "Bearer test-token")
                        .param("storeId", "1")
                        .param("targetDate", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.data.rainHours[0]").value(17));
    }

    @Test
    void returnsEmptyForUnavailableDistrictData() throws Exception {
        when(service.districtBenchmark(1L)).thenReturn(ChatLookupResponse.empty());

        mvc.perform(get("/internal/v2/sales/district-benchmark")
                        .header("Authorization", "Bearer test-token").param("storeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EMPTY"))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    @Test
    void rejectsMissingStoreId() throws Exception {
        mvc.perform(get("/internal/v2/sales/weather").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidDate() throws Exception {
        mvc.perform(get("/internal/v2/sales/weather").header("Authorization", "Bearer test-token")
                        .param("storeId", "1")
                        .param("targetDate", "19-10-2026"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingInternalTokenBeforeLookingUpWeather() throws Exception {
        mvc.perform(get("/internal/v2/sales/weather").param("storeId", "1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsWrongInternalTokenBeforeLookingUpDistrict() throws Exception {
        mvc.perform(get("/internal/v2/sales/district-benchmark")
                        .header("Authorization", "Bearer wrong-token").param("storeId", "1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsWhenServerTokenIsNotConfigured() throws Exception {
        MockMvc withoutConfiguredToken = MockMvcBuilders.standaloneSetup(
                        new ChatSalesLookupController(service))
                .addFilters(new ChatSalesLookupAuthenticationFilter(""))
                .build();

        withoutConfiguredToken.perform(get("/internal/v2/sales/weather")
                        .header("Authorization", "Bearer test-token").param("storeId", "1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
