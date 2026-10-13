package com.memme.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class StoreLocationServiceTest {

    @Mock private KakaoLocalClient kakaoLocalClient;

    @Test
    void 주소를_좌표로_변환한다() {
        StoreCoordinates coordinates = new StoreCoordinates(new BigDecimal("37.4786"), new BigDecimal("126.8646"));
        when(kakaoLocalClient.findCoordinates("경기도 광명시 일직로 00")).thenReturn(Optional.of(coordinates));
        StoreLocationService service = new StoreLocationService(kakaoLocalClient);

        assertThat(service.resolveCoordinates("경기도 광명시 일직로 00")).contains(coordinates);
    }

    @Test
    void 주소가_비어있으면_카카오를_호출하지_않는다() {
        StoreLocationService service = new StoreLocationService(kakaoLocalClient);

        assertThat(service.resolveCoordinates(" ")).isEmpty();
        verify(kakaoLocalClient, never()).findCoordinates(" ");
    }

    @Test
    void 카카오_주소_조회_실패는_좌표없음으로_처리해_가입과_주소수정을_막지_않는다() {
        when(kakaoLocalClient.findCoordinates("경기도 광명시 일직로 00"))
                .thenThrow(new RestClientException("upstream unavailable"));
        StoreLocationService service = new StoreLocationService(kakaoLocalClient);

        assertThat(service.resolveCoordinates("경기도 광명시 일직로 00")).isEmpty();
    }

    @Test
    void 카카오_요청_타임아웃은_좌표없음으로_처리해_가입과_주소수정을_막지_않는다() {
        when(kakaoLocalClient.findCoordinates("경기도 광명시 일직로 00"))
                .thenThrow(new ResourceAccessException("request timed out"));
        StoreLocationService service = new StoreLocationService(kakaoLocalClient);

        assertThat(service.resolveCoordinates("경기도 광명시 일직로 00")).isEmpty();
    }

    @Test
    void 좌표로_행정동을_조회한다() {
        AdministrativeDistrict district = new AdministrativeDistrict("11680640", "서울특별시 강남구 역삼1동");
        when(kakaoLocalClient.findAdministrativeDistrict(new BigDecimal("126.8646"), new BigDecimal("37.4786")))
                .thenReturn(Optional.of(district));
        StoreLocationService service = new StoreLocationService(kakaoLocalClient);

        assertThat(service.findAdministrativeDistrict(new BigDecimal("126.8646"), new BigDecimal("37.4786")))
                .contains(district);
    }

    @Test
    void 행정동_조회실패는_빈값으로_반환한다() {
        when(kakaoLocalClient.findAdministrativeDistrict(new BigDecimal("126.8646"), new BigDecimal("37.4786")))
                .thenThrow(new RestClientException("upstream unavailable"));
        StoreLocationService service = new StoreLocationService(kakaoLocalClient);

        assertThat(service.findAdministrativeDistrict(new BigDecimal("126.8646"), new BigDecimal("37.4786")))
                .isEmpty();
    }
}
