package com.memme.service.auth;

import com.memme.dto.auth.BusinessVerificationRequest;
import com.memme.dto.auth.BusinessVerificationResponse;
import com.memme.entity.auth.BusinessVerification;
import com.memme.exception.BusinessStatusNotEligibleException;
import com.memme.exception.BusinessVerificationFailedException;
import com.memme.exception.InvalidBusinessNumberException;
import com.memme.repository.auth.BusinessVerificationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class BusinessVerificationService {

    private static final Pattern BUSINESS_REG_NUMBER_PATTERN = Pattern.compile("^\\d{10}$");

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
        validateBusinessRegNumber(request);

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

    private void validateBusinessRegNumber(BusinessVerificationRequest request) {
        if (request == null
                || request.businessRegNumber() == null
                || !BUSINESS_REG_NUMBER_PATTERN.matcher(request.businessRegNumber()).matches()) {
            throw new InvalidBusinessNumberException();
        }
    }

    private boolean isActiveBusiness(String businessRegNumber) {
        try {
            return businessStatusClient.isActive(businessRegNumber);
        } catch (RuntimeException exception) {
            throw new BusinessVerificationFailedException();
        }
    }
}
