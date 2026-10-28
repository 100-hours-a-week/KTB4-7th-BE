package com.memme.repository.noti;

import com.memme.entity.noti.FcmToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    List<FcmToken> findAllByUserId(Long userId);

    Optional<FcmToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from FcmToken token where token.tokenHash = :tokenHash")
    Optional<FcmToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    int deleteByUserIdAndTokenHash(Long userId, String tokenHash);

    int deleteByTokenHash(String tokenHash);
}
