package com.memme.service.noti;

import com.memme.entity.auth.User;
import com.memme.entity.noti.FcmToken;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.noti.FcmTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FcmTokenService {

    private final UserRepository userRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final Clock clock;

    public FcmTokenService(
            UserRepository userRepository,
            FcmTokenRepository fcmTokenRepository,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.fcmTokenRepository = fcmTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public void registerToken(Long userId, String rawToken) {
        User user = userRepository.findByIdAndDeletedAtIsNullForUpdate(userId)
                .orElseThrow(AuthenticationRequiredException::new);
        String tokenHash = hash(rawToken);
        LocalDateTime registeredAt = LocalDateTime.now(clock);
        Optional<FcmToken> existingToken = fcmTokenRepository.findByTokenHashForUpdate(tokenHash);

        if (existingToken.isPresent()) {
            existingToken.get().refreshFor(user, rawToken, tokenHash, registeredAt);
            return;
        }

        fcmTokenRepository.save(FcmToken.register(user, rawToken, tokenHash, registeredAt));
    }

    @Transactional
    public void unregisterToken(Long userId, String rawToken) {
        fcmTokenRepository.deleteByUserIdAndTokenHash(userId, hash(rawToken));
    }

    private String hash(String rawToken) {
        try {
            byte[] tokenHash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(tokenHash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
