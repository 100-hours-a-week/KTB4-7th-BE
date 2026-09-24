package com.memme.service.store;

import com.memme.dto.store.AddressSearchRequest;
import com.memme.dto.store.AddressSearchResponse;
import com.memme.exception.store.AddressSearchFailedException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class JusoAddressSearchClient implements AddressSearchClient {

    private static final String JUSO_BASE_URL = "https://business.juso.go.kr";
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final RestClient restClient;
    private final String confirmationKey;

    @Autowired
    public JusoAddressSearchClient(@Value("${JUSO_CONFM_KEY}") String confirmationKey) {
        this(RestClient.builder(), confirmationKey);
    }

    JusoAddressSearchClient(RestClient.Builder restClientBuilder, String confirmationKey) {
        this.restClient = restClientBuilder.baseUrl(JUSO_BASE_URL).build();
        this.confirmationKey = confirmationKey;
    }

    @Override
    public AddressSearchResponse search(AddressSearchRequest request) {
        int currentPage = request.cursor() == null || request.cursor().isBlank()
                ? 1
                : Integer.parseInt(request.cursor());
        int pageSize = request.size() == null ? DEFAULT_PAGE_SIZE : request.size();

        JusoApiResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/addrlink/addrLinkApi.do")
                        .queryParam("confmKey", confirmationKey)
                        .queryParam("currentPage", currentPage)
                        .queryParam("countPerPage", pageSize)
                        .queryParam("keyword", request.query())
                        .queryParam("resultType", "json")
                        .build())
                .retrieve()
                .body(JusoApiResponse.class);

        if (!"0".equals(response.results().common().errorCode())) {
            throw new AddressSearchFailedException();
        }

        List<AddressSearchResponse.Address> addresses = response.results().juso().stream()
                .map(address -> new AddressSearchResponse.Address(
                        address.zipNo(),
                        address.roadAddr(),
                        address.jibunAddr()
                ))
                .toList();

        String nextCursor = response.results().common().totalCount() > currentPage * pageSize
                ? Integer.toString(currentPage + 1)
                : null;

        return new AddressSearchResponse(addresses, nextCursor);
    }

    private record JusoApiResponse(JusoResults results) {
    }

    private record JusoResults(JusoCommon common, List<JusoAddress> juso) {
    }

    private record JusoCommon(int totalCount, int currentPage, int countPerPage, String errorCode, String errorMessage) {
    }

    private record JusoAddress(String zipNo, String roadAddr, String jibunAddr) {
    }
}
