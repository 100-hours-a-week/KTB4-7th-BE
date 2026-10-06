package com.memme.service.store;

import com.memme.dto.common.FieldError;
import com.memme.dto.store.StoreProfileUpdateRequest;
import com.memme.dto.store.StoreProfileUpdateResponse;
import com.memme.entity.store.BusinessVerification;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.exception.store.BusinessVerificationExpiredException;
import com.memme.exception.store.DuplicateBusinessRegistrationNumberException;
import com.memme.exception.store.EmptyStoreProfileUpdateException;
import com.memme.exception.store.InvalidStoreBusinessVerificationException;
import com.memme.exception.store.InvalidStoreProfileUpdateRequestException;
import com.memme.exception.store.StoreProfileNotFoundException;
import com.memme.repository.store.BusinessVerificationRepository;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreProfileUpdateService {

    private static final LocalTime NEXT_DAY_CLOSE_LIMIT = LocalTime.of(6, 0);
    private static final LocalTime MIDNIGHT = LocalTime.MIDNIGHT;

    private final StoreRepository storeRepository;
    private final StoreBusinessHoursRepository storeBusinessHoursRepository;
    private final BusinessVerificationRepository businessVerificationRepository;
    private final Clock clock;

    public StoreProfileUpdateService(
            StoreRepository storeRepository,
            StoreBusinessHoursRepository storeBusinessHoursRepository,
            BusinessVerificationRepository businessVerificationRepository,
            Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.storeBusinessHoursRepository = storeBusinessHoursRepository;
        this.businessVerificationRepository = businessVerificationRepository;
        this.clock = clock;
    }

    @Transactional
    public StoreProfileUpdateResponse updateProfile(Long userId, StoreProfileUpdateRequest request) {
        Store store = storeRepository.findByOwnerId(userId)
                .orElseThrow(StoreProfileNotFoundException::new);
        if (!hasChanges(request)) {
            throw new EmptyStoreProfileUpdateException();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        BusinessVerification businessVerification = validateBusinessRegistrationUpdate(store, request, now);
        if (request.businessHours() != null) {
            validateBusinessHours(request.businessHours());
        }

        if (hasStoreChanges(request)) {
            StoreProfileUpdateRequest.Address address = request.address();
            store.updateProfile(
                    request.storeName(),
                    address == null ? null : address.postalCode(),
                    address == null ? null : address.roadAddress(),
                    address == null ? null : address.addressDetail(),
                    now
            );
        }
        if (businessVerification != null) {
            store.updateBusinessRegistration(request.businessRegNumber(), businessVerification.getVerifiedAt(), now);
            businessVerification.markUsedAt(now);
        }

        List<StoreBusinessHours> businessHours = storeBusinessHoursRepository
                .findAllByStoreIdOrderByDayOfWeekAsc(store.getId());
        if (request.businessHours() != null) {
            updateBusinessHours(businessHours, request.businessHours(), now);
        }
        if (businessVerification != null) {
            flushBusinessRegistrationUpdate();
        }

        return toResponse(store, businessHours);
    }

    private void flushBusinessRegistrationUpdate() {
        try {
            storeRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (isBusinessRegistrationNumberConstraintViolation(exception)) {
                throw new DuplicateBusinessRegistrationNumberException();
            }
            throw exception;
        }
    }

    private boolean isBusinessRegistrationNumberConstraintViolation(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException
                    && "uk_stores_business_registration_no".equalsIgnoreCase(
                            constraintViolationException.getConstraintName()
                    )) {
                return true;
            }
            if (current.getMessage() != null
                    && current.getMessage().contains("uk_stores_business_registration_no")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasChanges(StoreProfileUpdateRequest request) {
        return hasStoreChanges(request)
                || request.businessRegNumber() != null
                || request.businessVerificationId() != null
                || request.businessHours() != null;
    }

    private BusinessVerification validateBusinessRegistrationUpdate(
            Store store,
            StoreProfileUpdateRequest request,
            LocalDateTime now
    ) {
        String businessRegNumber = request.businessRegNumber();
        Long verificationId = request.businessVerificationId();
        if (businessRegNumber == null && verificationId == null) {
            return null;
        }
        if (businessRegNumber == null || verificationId == null) {
            String missingField = businessRegNumber == null ? "businessRegNumber" : "businessVerificationId";
            throw new InvalidStoreProfileUpdateRequestException(List.of(
                    new FieldError(missingField, "사업자등록번호와 인증 결과를 함께 입력해 주세요.")
            ));
        }

        BusinessVerification verification = businessVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(InvalidStoreBusinessVerificationException::new);
        if (verification.isExpiredAt(now)) {
            throw new BusinessVerificationExpiredException();
        }
        if (verification.isUsed() || !verification.matchesBusinessRegNumber(businessRegNumber)) {
            throw new InvalidStoreBusinessVerificationException();
        }
        if (storeRepository.existsByBusinessRegistrationNoAndIdNot(businessRegNumber, store.getId())) {
            throw new DuplicateBusinessRegistrationNumberException();
        }
        return verification;
    }

    private boolean hasStoreChanges(StoreProfileUpdateRequest request) {
        return request.storeName() != null || hasAddressChanges(request.address());
    }

    private boolean hasAddressChanges(StoreProfileUpdateRequest.Address address) {
        return address != null && (
                address.postalCode() != null
                        || address.roadAddress() != null
                        || address.addressDetail() != null
        );
    }

    private void validateBusinessHours(List<StoreProfileUpdateRequest.BusinessHours> businessHours) {
        Set<DayOfWeek> days = businessHours.stream()
                .map(StoreProfileUpdateRequest.BusinessHours::dayOfWeek)
                .collect(Collectors.toSet());
        if (businessHours.size() != 7 || days.size() != 7) {
            throw invalidBusinessHours();
        }

        for (StoreProfileUpdateRequest.BusinessHours businessHour : businessHours) {
            if (businessHour.isClosed()) {
                if (businessHour.openTime() != null || businessHour.closeTime() != null) {
                    throw invalidBusinessHours();
                }
                continue;
            }
            if (businessHour.openTime() == null || businessHour.closeTime() == null) {
                throw invalidBusinessHours();
            }

            LocalTime openTime = LocalTime.parse(businessHour.openTime());
            LocalTime closeTime = LocalTime.parse(businessHour.closeTime());
            if (openTime.equals(closeTime) && !openTime.equals(MIDNIGHT)) {
                throw invalidBusinessHours();
            }
            if (closeTime.isBefore(openTime) && closeTime.isAfter(NEXT_DAY_CLOSE_LIMIT)) {
                throw invalidBusinessHours();
            }
        }
    }

    private void updateBusinessHours(
            List<StoreBusinessHours> currentBusinessHours,
            List<StoreProfileUpdateRequest.BusinessHours> requestedBusinessHours,
            LocalDateTime updatedAt
    ) {
        Map<Integer, StoreBusinessHours> currentByDay = currentBusinessHours.stream()
                .collect(Collectors.toMap(StoreBusinessHours::getDayOfWeek, Function.identity()));

        for (StoreProfileUpdateRequest.BusinessHours requestedBusinessHour : requestedBusinessHours) {
            StoreBusinessHours currentBusinessHour = currentByDay.get(requestedBusinessHour.dayOfWeek().getValue());
            if (currentBusinessHour == null) {
                throw new IllegalStateException("요일별 영업시간 정보를 찾을 수 없습니다.");
            }
            currentBusinessHour.updateBusinessHours(
                    requestedBusinessHour.isClosed() ? null : LocalTime.parse(requestedBusinessHour.openTime()),
                    requestedBusinessHour.isClosed() ? null : LocalTime.parse(requestedBusinessHour.closeTime()),
                    requestedBusinessHour.isClosed(),
                    updatedAt
            );
        }
    }

    private StoreProfileUpdateResponse toResponse(Store store, List<StoreBusinessHours> businessHours) {
        return new StoreProfileUpdateResponse(new StoreProfileUpdateResponse.Store(
                store.getId(),
                store.getStoreName(),
                new StoreProfileUpdateResponse.Address(
                        store.getPostalCode(),
                        store.getAddress(),
                        store.getAddressDetail()
                ),
                businessHours.stream().map(this::toBusinessHours).toList()
        ));
    }

    private StoreProfileUpdateResponse.BusinessHours toBusinessHours(StoreBusinessHours businessHours) {
        boolean isClosed = businessHours.isClosed();
        return new StoreProfileUpdateResponse.BusinessHours(
                DayOfWeek.of(businessHours.getDayOfWeek()),
                isClosed,
                isClosed ? null : businessHours.getOpensAt(),
                isClosed ? null : businessHours.getClosesAt()
        );
    }

    private InvalidStoreProfileUpdateRequestException invalidBusinessHours() {
        return new InvalidStoreProfileUpdateRequestException(List.of(
                new FieldError("businessHours", "요일별 영업시간을 확인해 주세요.")
        ));
    }
}
