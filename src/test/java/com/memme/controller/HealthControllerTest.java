package com.memme.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

    @Test
    void health_endpoint_returns_ok() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();

        mockMvc.perform(MockMvcRequestBuilders.get("/health"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("ok"));
    }
}
