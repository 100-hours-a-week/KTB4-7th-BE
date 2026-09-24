package com.memme.service.store;

import com.memme.dto.common.FieldError;
import com.memme.dto.store.StoreProfileUpdateRequest;
import com.memme.dto.store.StoreProfileUpdateResponse;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.exception.store.EmptyStoreProfileUpdateException;
import com.memme.exception.store.InvalidStoreProfileUpdateRequestException;
import com.memme.exception.store.StoreProfileNotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreProfileUpdateService {

    private static final LocalTime NEXT_DAY_CLOSE_LIMIT = LocalTime.of(6, 0);

    private final StoreRepository storeRepository;
    private final StoreBusinessHoursRepository storeBusinessHoursRepository;
    private final Clock clock;

    public StoreProfileUpdateService(
            StoreRepository storeRepository,
            StoreBusinessHoursRepository storeBusinessHoursRepository,
            Clock clock
    ) {
        this.storeRepository = storeRepository;
        this.storeBusinessHoursRepository = storeBusinessHoursRepository;
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

        List<StoreBusinessHours> businessHours = storeBusinessHoursRepository
                .findAllByStoreIdOrderByDayOfWeekAsc(store.getId());
        if (request.businessHours() != null) {
            validateBusinessHours(request.businessHours());
            updateBusinessHours(businessHours, request.businessHours(), now);
        }

        return toResponse(store, businessHours);
    }

    private boolean hasChanges(StoreProfileUpdateRequest request) {
        return hasStoreChanges(request) || request.businessHours() != null;
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
