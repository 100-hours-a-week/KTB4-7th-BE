package com.memme.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

class ProductionRedisSessionFailureTest {

    @Test
    void returnsTheExistingGenericServerErrorWhenRedisSessionLookupFails() throws Exception {
        @SuppressWarnings("unchecked")
        SessionRepository<Session> repository = mock(SessionRepository.class);
        when(repository.findById("redis-down-session"))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));

        var resolver = new CookieHttpSessionIdResolver();
        var cookieSerializer = new ProductionRedisSessionConfig().cookieSerializer();
        resolver.setCookieSerializer(cookieSerializer);
        var filter = new SessionRepositoryFilter<>(repository);
        filter.setHttpSessionIdResolver(resolver);

        var response = new MockHttpServletResponse();
        cookieSerializer.writeCookieValue(new CookieValue(
                new MockHttpServletRequest(), response, "redis-down-session"));
        String setCookie = response.getHeader("Set-Cookie");
        String cookieValue = setCookie.substring("JSESSIONID=".length(), setCookie.indexOf(';'));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SessionAccessController())
                .addFilters(new ProductionRedisSessionFailureFilter(new ObjectMapper()), filter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/test/session").cookie(new Cookie("JSESSIONID", cookieValue)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @RestController
    private static class SessionAccessController {

        @GetMapping("/test/session")
        public void readSession(HttpServletRequest request) {
            request.getSession(false);
        }
    }
}
