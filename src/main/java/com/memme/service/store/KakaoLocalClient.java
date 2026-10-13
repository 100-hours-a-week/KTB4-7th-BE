package com.memme.service.store;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoLocalClient {

    private static final String KAKAO_BASE_URL = "https://dapi.kakao.com";
    private static final String AUTHORIZATION_PREFIX = "KakaoAK ";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final RestClient restClient;
    private final String restApiKey;

    @Autowired
    public KakaoLocalClient(@Value("${KAKAO_REST_API_KEY:}") String restApiKey) {
        this(createRestClientBuilder(), restApiKey);
    }

    KakaoLocalClient(RestClient.Builder restClientBuilder, String restApiKey) {
        this.restClient = restClientBuilder.baseUrl(KAKAO_BASE_URL).build();
        this.restApiKey = restApiKey == null ? "" : restApiKey.trim();
    }

    public Optional<StoreCoordinates> findCoordinates(String address) {
        if (restApiKey.isBlank() || address == null || address.isBlank()) {
            return Optional.empty();
        }

        KakaoAddressSearchResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/search/address.json")
                        .queryParam("query", address)
                        .build())
                .header("Authorization", AUTHORIZATION_PREFIX + restApiKey)
                .retrieve()
                .body(KakaoAddressSearchResponse.class);

        if (response == null || response.documents() == null || response.documents().isEmpty()) {
            return Optional.empty();
        }

        KakaoAddressDocument document = response.documents().getFirst();
        return coordinatesOf(document.roadAddress())
                .or(() -> coordinatesOf(document.address()));
    }

    public Optional<AdministrativeDistrict> findAdministrativeDistrict(BigDecimal longitude, BigDecimal latitude) {
        if (restApiKey.isBlank() || longitude == null || latitude == null) {
            return Optional.empty();
        }

        KakaoRegionCodeResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/geo/coord2regioncode.json")
                        .queryParam("x", longitude.toPlainString())
                        .queryParam("y", latitude.toPlainString())
                        .build())
                .header("Authorization", AUTHORIZATION_PREFIX + restApiKey)
                .retrieve()
                .body(KakaoRegionCodeResponse.class);

        if (response == null || response.documents() == null) {
            return Optional.empty();
        }

        return response.documents().stream()
                .filter(document -> "H".equals(document.regionType()))
                .findFirst()
                .flatMap(this::toAdministrativeDistrict);
    }

    private Optional<StoreCoordinates> coordinatesOf(KakaoAddressCoordinate address) {
        if (address == null || address.x() == null || address.y() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StoreCoordinates(new BigDecimal(address.y()), new BigDecimal(address.x())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Optional<AdministrativeDistrict> toAdministrativeDistrict(KakaoRegionDocument document) {
        if (document.code() == null || document.addressName() == null) {
            return Optional.empty();
        }

        String seoulCode = toEightDigitAdministrativeDistrictCode(document.code());
        if (seoulCode == null) {
            return Optional.empty();
        }
        return Optional.of(new AdministrativeDistrict(seoulCode, document.addressName()));
    }

    private String toEightDigitAdministrativeDistrictCode(String kakaoCode) {
        if (kakaoCode.matches("\\d{8}")) {
            return kakaoCode;
        }
        if (kakaoCode.matches("\\d{10}") && kakaoCode.endsWith("00")) {
            return kakaoCode.substring(0, 8);
        }
        return null;
    }

    private static RestClient.Builder createRestClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }

    private record KakaoAddressSearchResponse(List<KakaoAddressDocument> documents) {
    }

    private record KakaoAddressDocument(
            KakaoAddressCoordinate address,
            @JsonProperty("road_address") KakaoAddressCoordinate roadAddress
    ) {
    }

    private record KakaoAddressCoordinate(String x, String y) {
    }

    private record KakaoRegionCodeResponse(List<KakaoRegionDocument> documents) {
    }

    private record KakaoRegionDocument(
            @JsonProperty("region_type")
            String regionType,
            String code,
            @JsonProperty("address_name")
            String addressName
    ) {
    }
}
