package com.memme.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ChatSalesLookupAuthenticationFilter extends OncePerRequestFilter {

    private static final String WEATHER_PATH = "/internal/v2/sales/weather";
    private static final String DISTRICT_PATH = "/internal/v2/sales/district-benchmark";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String expectedToken;

    public ChatSalesLookupAuthenticationFilter(@Value("${INTERNAL_AI_TOKEN:}") String expectedToken) {
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !WEATHER_PATH.equals(path) && !DISTRICT_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (expectedToken.isEmpty() || authorization == null
                || !authorization.startsWith(BEARER_PREFIX)
                || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
                authorization.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
