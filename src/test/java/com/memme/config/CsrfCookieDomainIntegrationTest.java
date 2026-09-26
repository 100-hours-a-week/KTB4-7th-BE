package com.memme.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@TestPropertySource(properties = {
        "CSRF_COOKIE_DOMAIN=memme.kr",
        "CSRF_COOKIE_SECURE=true",
        "CSRF_COOKIE_SAME_SITE=Lax"
})
class CsrfCookieDomainIntegrationTest {

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
    void 배포_환경에서는_CSRF_쿠키에_공유_도메인을_설정한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/auth/csrf-token"))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");

        assertNotNull(csrfCookie);
        assertEquals("memme.kr", csrfCookie.getDomain());
        assertEquals(true, csrfCookie.getSecure());
        assertEquals("Lax", csrfCookie.getAttribute("SameSite"));
    }

    @Test
    void 기존_API_호스트_쿠키를_공유_도메인_쿠키로_이전한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/auth/csrf-token")
                        .cookie(new Cookie("XSRF-TOKEN", "legacy-token")))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertTrue(setCookies.stream().anyMatch(cookie -> cookie.contains("XSRF-TOKEN=legacy-token")
                && cookie.contains("Domain=memme.kr")), setCookies.toString());
        assertTrue(setCookies.stream().anyMatch(cookie -> cookie.contains("XSRF-TOKEN=")
                && cookie.contains("Max-Age=0") && !cookie.contains("Domain=")));
    }

    @Test
    void 빈_기존_쿠키가_있어도_새_공유_도메인_토큰을_발급한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/auth/csrf-token")
                        .cookie(new Cookie("XSRF-TOKEN", "")))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertTrue(setCookies.stream().anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=")
                && !cookie.startsWith("XSRF-TOKEN=;") && cookie.contains("Domain=memme.kr")),
                setCookies.toString());
        assertTrue(setCookies.stream().anyMatch(cookie -> cookie.contains("Max-Age=0")
                && !cookie.contains("Domain=")), setCookies.toString());
    }
}
