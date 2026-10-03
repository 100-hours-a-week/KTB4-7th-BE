package com.memme.service.auth;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetRateLimitKeyHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] hashKey;

    public PasswordResetRateLimitKeyHasher(
            @Value("${app.password-reset.rate-limit.hash-key:}") String configuredHashKey,
            @Value("${DB_PASSWORD:}") String databasePassword
    ) {
        String selectedKey = configuredHashKey.isBlank() ? databasePassword : configuredHashKey;
        if (selectedKey.isBlank()) {
            throw new IllegalStateException(
                    "PASSWORD_RESET_RATE_LIMIT_HASH_KEY 또는 DB_PASSWORD 환경변수가 필요합니다."
            );
        }
        this.hashKey = selectedKey.getBytes(StandardCharsets.UTF_8);
    }

    public String hashEmail(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return hmac("email:" + normalizedEmail);
    }

    public String hashIp(String ipAddress) {
        return hmac("ip:" + ipAddress);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hashKey, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("요청 제한 키 해시를 생성할 수 없습니다.", exception);
        }
    }
}
