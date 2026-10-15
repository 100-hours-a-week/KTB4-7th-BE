package com.memme.service.solution.external;

import com.memme.dto.solution.external.KmaVillageForecastResponse;
import com.memme.exception.solution.ExternalDataLookupException;
import com.memme.service.store.StoreCoordinates;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class KmaVillageForecastService {

    private static final String BASE_URL = "https://apis.data.go.kr";
    private static final String PATH = "/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int[] ISSUE_HOURS = {23, 20, 17, 14, 11, 8, 5, 2};
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final int PAGE_SIZE = 1000;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;
    private final String serviceKey;
    private final Clock clock;

    @Autowired
    public KmaVillageForecastService(@Value("${DATA_GO_KR_SERVICE_KEY:}") String serviceKey) {
        this(productionBuilder(), serviceKey, Clock.system(KOREA));
    }

    KmaVillageForecastService(RestClient.Builder builder, String serviceKey, Clock clock) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.clock = clock;
    }

    public Optional<KmaVillageForecastResponse> fetch(StoreCoordinates coordinates) {
        if (serviceKey.isBlank()) {
            throw new ExternalDataLookupException("KMA_KEY_MISSING");
        }
        if (coordinates == null) {
            return Optional.empty();
        }
        LocalDateTime issue = latestAvailableIssue(LocalDateTime.now(clock.withZone(KOREA)));
        return fetchForIssue(coordinates, issue);
    }

    public Optional<KmaVillageForecastResponse> fetchEarlyIssue(
            StoreCoordinates coordinates,
            LocalDate targetDate
    ) {
        if (serviceKey.isBlank()) {
            throw new ExternalDataLookupException("KMA_KEY_MISSING");
        }
        if (coordinates == null || targetDate == null) {
            return Optional.empty();
        }
        LocalDateTime issue = targetDate.atTime(2, 0);
        if (LocalDateTime.now(clock.withZone(KOREA)).isBefore(issue.plusMinutes(10))) {
            return Optional.empty();
        }
        return fetchForIssue(coordinates, issue);
    }

    private Optional<KmaVillageForecastResponse> fetchForIssue(
            StoreCoordinates coordinates,
            LocalDateTime issue
    ) {
        KmaGrid grid = KmaGrid.from(coordinates);
        KmaVillageForecastResponse payload = fetchPage(issue, grid, 1);
        KmaVillageForecastResponse.Body body = payload.response().body();
        if (body == null || body.items() == null || body.items().item() == null
                || body.items().item().isEmpty()) {
            return Optional.empty();
        }
        int totalCount = body.totalCount() == null ? body.items().item().size() : body.totalCount();
        if (totalCount <= PAGE_SIZE) {
            return Optional.of(payload);
        }
        List<KmaVillageForecastResponse.Item> items = new ArrayList<>(body.items().item());
        for (int page = 2; page <= (totalCount + PAGE_SIZE - 1) / PAGE_SIZE; page++) {
            KmaVillageForecastResponse.Body nextBody = fetchPage(issue, grid, page).response().body();
            if (nextBody == null || nextBody.items() == null || nextBody.items().item() == null) {
                throw new ExternalDataLookupException("KMA_INVALID_RESPONSE");
            }
            items.addAll(nextBody.items().item());
        }
        KmaVillageForecastResponse.Body combined = new KmaVillageForecastResponse.Body(
                body.dataType(), body.numOfRows(), body.pageNo(), totalCount,
                new KmaVillageForecastResponse.Items(List.copyOf(items)));
        return Optional.of(new KmaVillageForecastResponse(
                new KmaVillageForecastResponse.Response(payload.response().header(), combined)));
    }

    private KmaVillageForecastResponse fetchPage(LocalDateTime issue, KmaGrid grid, int page) {
        URI uri;
        try {
            uri = URI.create(BASE_URL + PATH + "?serviceKey=" + serviceKey
                    + "&base_date=" + issue.toLocalDate().format(DATE)
                    + "&base_time=" + issue.toLocalTime().format(TIME)
                    + "&nx=" + grid.x() + "&ny=" + grid.y()
                    + "&dataType=JSON&numOfRows=" + PAGE_SIZE + "&pageNo=" + page);
        } catch (IllegalArgumentException exception) {
            throw new ExternalDataLookupException("KMA_KEY_INVALID");
        }
        String rawResponse;
        try {
            rawResponse = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ExternalDataLookupException(status == 401 || status == 403
                    ? "KMA_AUTH_ERROR" : "KMA_HTTP_ERROR");
        } catch (ResourceAccessException exception) {
            throw new ExternalDataLookupException(isTimeout(exception)
                    ? "KMA_TIMEOUT" : "KMA_CONNECTION_ERROR");
        } catch (RestClientException exception) {
            throw new ExternalDataLookupException("KMA_INVALID_RESPONSE");
        }
        JsonNode response;
        try {
            response = JSON.readTree(rawResponse).path("response");
        } catch (Exception exception) {
            throw new ExternalDataLookupException("KMA_INVALID_RESPONSE");
        }
        if (!"00".equals(response.path("header").path("resultCode").asString())) {
            throw new ExternalDataLookupException("KMA_PROVIDER_ERROR");
        }
        if (response.path("body").path("totalCount").isMissingNode()) {
            throw new ExternalDataLookupException("KMA_INVALID_RESPONSE");
        }
        if (response.path("body").path("totalCount").asInt(-1) == 0) {
            return new KmaVillageForecastResponse(new KmaVillageForecastResponse.Response(
                    new KmaVillageForecastResponse.Header("00", response.path("header").path("resultMsg").asString()),
                    null));
        }
        try {
            return JSON.readValue(rawResponse, KmaVillageForecastResponse.class);
        } catch (Exception exception) {
            throw new ExternalDataLookupException("KMA_INVALID_RESPONSE");
        }
    }

    private static boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    static LocalDateTime latestAvailableIssue(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        for (int hour : ISSUE_HOURS) {
            LocalDateTime available = LocalDateTime.of(date, LocalTime.of(hour, 10));
            if (!now.isBefore(available)) {
                return available.minusMinutes(10);
            }
        }
        return LocalDateTime.of(date.minusDays(1), LocalTime.of(23, 0));
    }

    private static RestClient.Builder productionBuilder() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().requestFactory(factory);
    }
}
