package com.memme.config;

import com.memme.dto.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String CSRF_ERROR_MESSAGE = "CSRF 토큰이 유효하지 않습니다.";

    private final CorsConfigurationSource corsConfigurationSource;
    private final ObjectMapper objectMapper;
    private final String csrfCookieDomain;
    private final boolean csrfCookieSecure;
    private final String csrfCookieSameSite;

    public SecurityConfig(
            CorsConfigurationSource corsConfigurationSource,
            ObjectMapper objectMapper,
            @Value("${CSRF_COOKIE_DOMAIN:}") String csrfCookieDomain,
            @Value("${CSRF_COOKIE_SECURE:false}") boolean csrfCookieSecure,
            @Value("${CSRF_COOKIE_SAME_SITE:Lax}") String csrfCookieSameSite
    ) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.objectMapper = objectMapper;
        this.csrfCookieDomain = csrfCookieDomain;
        this.csrfCookieSecure = csrfCookieSecure;
        this.csrfCookieSameSite = csrfCookieSameSite;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf
                        .spa()
                        .csrfTokenRepository(csrfTokenRepository()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception.accessDeniedHandler(csrfAccessDeniedHandler()))
                .build();
    }

    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> {
            if (!csrfCookieDomain.isBlank()) {
                cookie.domain(csrfCookieDomain);
            }
            cookie.secure(csrfCookieSecure);
            cookie.sameSite(csrfCookieSameSite);
        });
        return repository;
    }

    private AccessDeniedHandler csrfAccessDeniedHandler() {
        return this::writeCsrfErrorResponse;
    }

    private void writeCsrfErrorResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException exception
    ) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiResponse<>(CSRF_ERROR_MESSAGE, null)
        );
    }
}
