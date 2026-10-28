package com.memme.repository.noti;

import com.memme.entity.noti.FcmToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    List<FcmToken> findAllByUserId(Long userId);

    Optional<FcmToken> findByTokenHash(String tokenHash);

    int deleteByUserIdAndTokenHash(Long userId, String tokenHash);

    int deleteByTokenHash(String tokenHash);
}
