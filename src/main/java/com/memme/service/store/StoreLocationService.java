package com.memme.service.store;

import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class StoreLocationService {

    private static final Logger log = LoggerFactory.getLogger(StoreLocationService.class);

    private final KakaoLocalClient kakaoLocalClient;

    public StoreLocationService(KakaoLocalClient kakaoLocalClient) {
        this.kakaoLocalClient = kakaoLocalClient;
    }

    public Optional<StoreCoordinates> resolveCoordinates(String roadAddress) {
        if (roadAddress == null || roadAddress.isBlank()) {
            return Optional.empty();
        }
        try {
            return kakaoLocalClient.findCoordinates(roadAddress);
        } catch (RestClientException exception) {
            log.warn("Kakao Local address lookup failed; store will be saved without coordinates ({})",
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public Optional<AdministrativeDistrict> findAdministrativeDistrict(BigDecimal longitude, BigDecimal latitude) {
        if (longitude == null || latitude == null) {
            return Optional.empty();
        }
        try {
            return kakaoLocalClient.findAdministrativeDistrict(longitude, latitude);
        } catch (RestClientException exception) {
            log.warn("Kakao Local administrative district lookup failed ({})",
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
