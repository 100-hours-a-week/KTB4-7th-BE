package com.memme.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class CsrfSecurityIntegrationTest {

    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Collection<Filter> filters = webApplicationContext.getBeansOfType(Filter.class).values();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(filters.toArray(Filter[]::new))
                .build();
    }

    @Test
    void CSRF_토큰_발급_요청은_204와_XSRF_TOKEN_쿠키를_반환한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/auth/csrf-token"))
                .andExpect(status().isNoContent())
                .andReturn();

        assertNotNull(result.getResponse().getCookie(CSRF_COOKIE_NAME));
    }

    @Test
    void CSRF_토큰_없이_상태_변경_요청을_보내면_403_공통_응답을_반환한다() throws Exception {
        mockMvc.perform(post("/v1/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CSRF 토큰이 유효하지 않습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void CSRF_쿠키와_헤더가_일치하면_상태_변경_요청을_처리한다() throws Exception {
        MvcResult tokenResult = mockMvc.perform(get("/v1/auth/csrf-token"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie csrfCookie = tokenResult.getResponse().getCookie(CSRF_COOKIE_NAME);
        assertNotNull(csrfCookie);

        mockMvc.perform(post("/v1/auth/logout")
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    void GET과_CORS_preflight_요청은_CSRF_토큰_없이_허용한다() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());

        mockMvc.perform(options("/v1/auth/logout")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
