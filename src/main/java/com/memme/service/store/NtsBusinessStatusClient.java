package com.memme.service.store;

import java.util.List;

import com.memme.exception.store.BusinessVerificationFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 공공데이터포털 국세청 사업자등록상태 조회 API를 실제로 호출하는 구현체.
 *
 * <p>{@code local} 프로필에서는 {@link MockBusinessStatusClient}가 대신 등록되므로, 실제 사업자
 * 등록번호가 없는 로컬 개발/테스트 환경에서도 회원가입 플로우를 끝까지 확인할 수 있다. 운영/기본
 * 프로필에서만 이 구현체가 활성화된다.
 */
@Component
@Profile("!local")
public class NtsBusinessStatusClient implements BusinessStatusClient {

    private static final String NTS_BASE_URL = "https://api.odcloud.kr/api/nts-businessman/v1";
    private static final String ACTIVE_STATUS_CODE = "01";

    private final RestClient restClient;
    private final String serviceKey;

    public NtsBusinessStatusClient(@Value("${NTS_SERVICE_KEY}") String serviceKey) {
        this.restClient = RestClient.builder()
                .baseUrl(NTS_BASE_URL)
                .build();
        this.serviceKey = serviceKey;
    }

    @Override
    public boolean isActive(String businessRegNumber) {
        NtsStatusResponse response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/status")
                        .queryParam("serviceKey", serviceKey)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new NtsStatusRequest(List.of(businessRegNumber)))
                .retrieve()
                .body(NtsStatusResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new BusinessVerificationFailedException();
        }

        return ACTIVE_STATUS_CODE.equals(response.data().getFirst().b_stt_cd());
    }

    private record NtsStatusRequest(List<String> b_no) {
    }

    private record NtsStatusResponse(List<NtsStatusData> data) {
    }

    private record NtsStatusData(String b_stt_cd) {
    }
}
