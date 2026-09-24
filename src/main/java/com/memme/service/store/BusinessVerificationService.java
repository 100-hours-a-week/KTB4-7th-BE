package com.memme.service.store;

import com.memme.dto.store.BusinessVerificationRequest;
import com.memme.dto.store.BusinessVerificationResponse;
import com.memme.entity.store.BusinessVerification;
import com.memme.exception.store.BusinessStatusNotEligibleException;
import com.memme.exception.store.BusinessVerificationFailedException;
import com.memme.repository.store.BusinessVerificationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class BusinessVerificationService {

    private final BusinessVerificationRepository businessVerificationRepository;
    private final BusinessStatusClient businessStatusClient;
    private final Clock clock;

    public BusinessVerificationService(
            BusinessVerificationRepository businessVerificationRepository,
            BusinessStatusClient businessStatusClient,
            Clock clock
    ) {
        this.businessVerificationRepository = businessVerificationRepository;
        this.businessStatusClient = businessStatusClient;
        this.clock = clock;
    }

    public BusinessVerificationResponse verify(BusinessVerificationRequest request) {
        if (!isActiveBusiness(request.businessRegNumber())) {
            throw new BusinessStatusNotEligibleException();
        }

        OffsetDateTime verifiedAt = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = verifiedAt.plusMinutes(10);
        BusinessVerification businessVerification = BusinessVerification.create(
                request.businessRegNumber(),
                verifiedAt.toLocalDateTime(),
                expiresAt.toLocalDateTime(),
                verifiedAt.toLocalDateTime()
        );
        BusinessVerification savedBusinessVerification = businessVerificationRepository.save(businessVerification);

        return new BusinessVerificationResponse(savedBusinessVerification.getId(), expiresAt);
    }

    private boolean isActiveBusiness(String businessRegNumber) {
        try {
            return businessStatusClient.isActive(businessRegNumber);
        } catch (RuntimeException exception) {
            throw new BusinessVerificationFailedException();
        }
    }
}
