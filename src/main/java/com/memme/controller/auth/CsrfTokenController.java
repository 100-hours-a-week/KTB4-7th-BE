package com.memme.controller.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class CsrfTokenController {

    private final CookieCsrfTokenRepository csrfTokenRepository;
    private final String csrfCookieDomain;
    private final boolean csrfCookieSecure;
    private final String csrfCookieSameSite;

    public CsrfTokenController(
            CookieCsrfTokenRepository csrfTokenRepository,
            @Value("${CSRF_COOKIE_DOMAIN:}") String csrfCookieDomain,
            @Value("${CSRF_COOKIE_SECURE:false}") boolean csrfCookieSecure,
            @Value("${CSRF_COOKIE_SAME_SITE:Lax}") String csrfCookieSameSite
    ) {
        this.csrfTokenRepository = csrfTokenRepository;
        this.csrfCookieDomain = csrfCookieDomain;
        this.csrfCookieSecure = csrfCookieSecure;
        this.csrfCookieSameSite = csrfCookieSameSite;
    }

    @GetMapping("/csrf-token")
    public ResponseEntity<Void> issueToken(
            CsrfToken csrfToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        csrfToken.getToken();
        if (!csrfCookieDomain.isBlank() && request.getCookies() != null
                && Arrays.stream(request.getCookies())
                        .map(Cookie::getName)
                        .anyMatch("XSRF-TOKEN"::equals)) {
            CsrfToken storedToken = csrfTokenRepository.loadToken(request);
            if (storedToken != null) {
                csrfTokenRepository.saveToken(storedToken, request, response);
            }
            ResponseCookie expiredHostCookie = ResponseCookie.from("XSRF-TOKEN", "")
                    .path("/")
                    .maxAge(0)
                    .secure(csrfCookieSecure)
                    .sameSite(csrfCookieSameSite)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, expiredHostCookie.toString());
        }
        return ResponseEntity.noContent().build();
    }
}
