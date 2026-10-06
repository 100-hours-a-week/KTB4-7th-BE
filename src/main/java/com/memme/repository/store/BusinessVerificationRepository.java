package com.memme.repository.store;

import com.memme.entity.store.BusinessVerification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BusinessVerificationRepository extends JpaRepository<BusinessVerification, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select verification from BusinessVerification verification where verification.id = :verificationId")
    Optional<BusinessVerification> findByIdForUpdate(@Param("verificationId") Long verificationId);
}
