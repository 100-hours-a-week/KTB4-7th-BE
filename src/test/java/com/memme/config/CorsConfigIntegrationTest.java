package com.memme.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "CORS_ALLOWED_ORIGINS=http://localhost:5173, https://memme.kr")
class CorsConfigIntegrationTest {

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
    void 설정한_각_오리진의_preflight_요청을_허용한다() throws Exception {
        for (String origin : new String[] {"http://localhost:5173", "https://memme.kr"}) {
            mockMvc.perform(options("/v1/auth/logout")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }
    }

    @Test
    void 설정하지_않은_오리진의_preflight_요청을_거부한다() throws Exception {
        mockMvc.perform(options("/v1/auth/logout")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void 자격_증명과_함께_전체_오리진을_허용하는_설정은_거부한다() {
        assertThatThrownBy(() -> new CorsConfig().corsConfigurationSource("*"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
