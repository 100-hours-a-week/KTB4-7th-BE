package com.memme.service.store;

import com.memme.dto.store.StoreProfileResponse;
import com.memme.entity.store.Store;
import com.memme.entity.store.StoreBusinessHours;
import com.memme.exception.store.StoreProfileNotFoundException;
import com.memme.repository.store.StoreBusinessHoursRepository;
import com.memme.repository.store.StoreRepository;
import java.time.DayOfWeek;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StoreProfileService {

    private final StoreRepository storeRepository;
    private final StoreBusinessHoursRepository storeBusinessHoursRepository;

    public StoreProfileService(
            StoreRepository storeRepository,
            StoreBusinessHoursRepository storeBusinessHoursRepository
    ) {
        this.storeRepository = storeRepository;
        this.storeBusinessHoursRepository = storeBusinessHoursRepository;
    }

    public StoreProfileResponse getProfile(Long userId) {
        Store store = storeRepository.findByOwnerId(userId)
                .orElseThrow(StoreProfileNotFoundException::new);
        List<StoreProfileResponse.BusinessHours> businessHours = storeBusinessHoursRepository
                .findAllByStoreIdOrderByDayOfWeekAsc(store.getId())
                .stream()
                .map(this::toBusinessHours)
                .toList();

        return new StoreProfileResponse(new StoreProfileResponse.Store(
                store.getId(),
                formatBusinessRegistrationNo(store.getBusinessRegistrationNo()),
                store.getStoreName(),
                new StoreProfileResponse.Address(
                        store.getPostalCode(),
                        store.getAddress(),
                        store.getAddressDetail()
                ),
                businessHours
        ));
    }

    private StoreProfileResponse.BusinessHours toBusinessHours(StoreBusinessHours businessHours) {
        boolean isClosed = businessHours.isClosed();
        return new StoreProfileResponse.BusinessHours(
                DayOfWeek.of(businessHours.getDayOfWeek()),
                isClosed,
                isClosed ? null : businessHours.getOpensAt(),
                isClosed ? null : businessHours.getClosesAt()
        );
    }

    private String formatBusinessRegistrationNo(String businessRegistrationNo) {
        return businessRegistrationNo.substring(0, 3) + "-"
                + businessRegistrationNo.substring(3, 5) + "-"
                + businessRegistrationNo.substring(5);
    }
}
