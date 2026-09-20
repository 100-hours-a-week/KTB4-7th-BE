package com.memme.repository.auth;

import com.memme.entity.auth.BusinessVerification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessVerificationRepository extends JpaRepository<BusinessVerification, Long> {
}
