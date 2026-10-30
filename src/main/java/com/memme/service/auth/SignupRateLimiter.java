package com.memme.service.auth;

import com.memme.exception.auth.SignupRateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 가입 1단계 요청을 IP별로 태스크 메모리에서 센다. BCrypt 전에 거절해, WAF가 차단을 시작하기 전의 폭주도 CPU를 쓰지 않게 한다.
 * 태스크마다 따로 세므로 서비스 전체 한도는 태스크 수만큼 늘어난다.
 */
@Component
public class SignupRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SignupRateLimiter.class);

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final Duration window;
    private final int maxTrackedIps;
    private final Clock clock;

    public SignupRateLimiter(
            @Value("${app.signup-rate-limit.max-requests}") int maxRequests,
            @Value("${app.signup-rate-limit.window-seconds}") long windowSeconds,
            @Value("${app.signup-rate-limit.max-tracked-ips}") int maxTrackedIps,
            Clock clock
    ) {
        if (maxRequests < 1 || windowSeconds < 1 || maxTrackedIps < 1) {
            throw new IllegalArgumentException("Signup rate limit settings must be positive");
        }
        this.maxRequests = maxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
        this.maxTrackedIps = maxTrackedIps;
        this.clock = clock;
    }

    public void checkAllowed(String ipAddress) {
        Instant now = clock.instant();
        if (windows.size() >= maxTrackedIps) {
            windows.entrySet().removeIf(entry -> entry.getValue().expiredAt(now));
            if (windows.size() >= maxTrackedIps && !windows.containsKey(ipAddress)) {
                // 추적 한도를 넘으면 새 IP는 세지 않고 통과시킨다. 비밀번호 해시 게이트가 CPU를 따로 보호한다.
                return;
            }
        }
        Window current = windows.compute(ipAddress, (key, existing) ->
                existing == null || existing.expiredAt(now) ? new Window(now.plus(window), 1) : existing.increment());
        if (current.count() > maxRequests) {
            if (current.count() == maxRequests + 1) {
                log.warn("Signup rate limit exceeded");
            }
            Duration retry = Duration.between(now, current.resetAt());
            throw new SignupRateLimitExceededException(retry.getSeconds() + (retry.getNano() == 0 ? 0 : 1));
        }
    }

    private record Window(Instant resetAt, int count) {

        boolean expiredAt(Instant now) {
            return !now.isBefore(resetAt);
        }

        Window increment() {
            return new Window(resetAt, count == Integer.MAX_VALUE ? count : count + 1);
        }
    }
}
