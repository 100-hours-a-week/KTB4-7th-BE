package com.memme.config;

import com.memme.dto.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("production-redis")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionRedisSessionFailureFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ProductionRedisSessionFailureFilter.class);
    private static final String INTERNAL_SERVER_ERROR_MESSAGE =
            "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

    private final ObjectMapper objectMapper;

    public ProductionRedisSessionFailureFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (RuntimeException | ServletException failure) {
            if (!isRedisConnectionFailure(failure) || response.isCommitted()) {
                throw failure;
            }
            LOG.error("Redis session request failed", failure);
            response.resetBuffer();
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), new ApiResponse<>(INTERNAL_SERVER_ERROR_MESSAGE, null));
        }
    }

    private boolean isRedisConnectionFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof RedisConnectionFailureException) {
                return true;
            }
            if (cause.getCause() == cause) {
                return false;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
