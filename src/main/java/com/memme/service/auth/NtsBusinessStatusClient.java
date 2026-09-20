package com.memme.service.auth;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
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
            throw new IllegalStateException("국세청 사업자 상태 조회 응답이 비어 있습니다.");
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
