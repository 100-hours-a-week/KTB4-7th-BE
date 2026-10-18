package com.memme.service.solution.external;

import com.memme.exception.solution.ExternalDataLookupException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class KmaAsosObservationClient {

    private static final String BASE_URL = "https://apis.data.go.kr";
    private static final String PATH = "/1360000/AsosDalyInfoService/getWthrDataList";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int PAGE_SIZE = 100;
    private static final List<Station> STATIONS = loadStations();

    private final RestClient restClient;
    private final String serviceKey;

    @Autowired
    public KmaAsosObservationClient(@Value("${DATA_GO_KR_SERVICE_KEY:}") String serviceKey) {
        this(productionBuilder(), serviceKey);
    }

    KmaAsosObservationClient(RestClient.Builder builder, String serviceKey) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
    }

    public List<Station> stations() {
        return STATIONS;
    }

    public Map<LocalDate, BigDecimal> dailyRain(String stationCode, LocalDate from, LocalDate to) {
        if (stationCode == null || !stationCode.matches("\\d{2,5}") || from == null || to == null
                || from.isAfter(to)) {
            throw new IllegalArgumentException("Invalid ASOS station or date range");
        }
        Map<LocalDate, BigDecimal> rain = new HashMap<>();
        int page = 1;
        int totalCount;
        do {
            JsonNode body = fetchPage(stationCode, from, to, page);
            totalCount = body.path("totalCount").asInt(-1);
            if (totalCount < 0) {
                throw new ExternalDataLookupException("ASOS_INVALID_RESPONSE");
            }
            if (totalCount > 0) {
                JsonNode items = body.path("items").path("item");
                if (!items.isArray() || items.isEmpty()) {
                    throw new ExternalDataLookupException("ASOS_INVALID_RESPONSE");
                }
                for (JsonNode item : items) {
                    addRain(rain, item, stationCode, from, to);
                }
            }
            page++;
        } while ((page - 1) * PAGE_SIZE < totalCount);
        return Map.copyOf(rain);
    }

    private JsonNode fetchPage(String stationCode, LocalDate from, LocalDate to, int page) {
        if (serviceKey.isBlank()) {
            throw new ExternalDataLookupException("ASOS_KEY_MISSING");
        }
        URI uri;
        try {
            uri = URI.create(BASE_URL + PATH + "?ServiceKey=" + serviceKey
                    + "&pageNo=" + page + "&numOfRows=" + PAGE_SIZE
                    + "&dataType=JSON&dataCd=ASOS&dateCd=DAY"
                    + "&startDt=" + from.format(DATE) + "&endDt=" + to.format(DATE)
                    + "&stnIds=" + stationCode);
        } catch (IllegalArgumentException exception) {
            throw new ExternalDataLookupException("ASOS_KEY_INVALID");
        }
        String raw;
        try {
            raw = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException exception) {
            throw new ExternalDataLookupException("ASOS_LOOKUP_FAILED");
        }
        try {
            JsonNode response = JSON.readTree(raw).path("response");
            if (!"00".equals(response.path("header").path("resultCode").asString())) {
                throw new ExternalDataLookupException("ASOS_PROVIDER_ERROR");
            }
            JsonNode body = response.path("body");
            if (!body.isObject()) {
                throw new ExternalDataLookupException("ASOS_INVALID_RESPONSE");
            }
            return body;
        } catch (ExternalDataLookupException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExternalDataLookupException("ASOS_INVALID_RESPONSE");
        }
    }

    private static void addRain(Map<LocalDate, BigDecimal> rain, JsonNode item, String stationCode,
                                LocalDate from, LocalDate to) {
        try {
            LocalDate date = LocalDate.parse(item.path("tm").asString());
            if (!stationCode.equals(item.path("stnId").asString())
                    || date.isBefore(from) || date.isAfter(to)) {
                throw new ExternalDataLookupException("ASOS_INVALID_RESPONSE");
            }
            String value = item.path("sumRn").asString().trim();
            if (!value.isEmpty()) {
                BigDecimal amount = new BigDecimal(value);
                if (amount.signum() >= 0) {
                    rain.put(date, amount);
                }
            }
        } catch (ExternalDataLookupException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalDataLookupException("ASOS_INVALID_RESPONSE");
        }
    }

    private static List<Station> loadStations() {
        List<Station> stations = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("solution/asos-stations.csv").getInputStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("station_code,")) {
                    continue;
                }
                String[] columns = line.split(",");
                if (columns.length != 3) {
                    throw new ExternalDataLookupException("ASOS_STATIONS_INVALID");
                }
                stations.add(new Station(columns[0], new BigDecimal(columns[1]),
                        new BigDecimal(columns[2])));
            }
        } catch (IOException | NumberFormatException exception) {
            throw new ExternalDataLookupException("ASOS_STATIONS_INVALID");
        }
        if (stations.isEmpty()) {
            throw new ExternalDataLookupException("ASOS_STATIONS_INVALID");
        }
        return List.copyOf(stations);
    }

    private static RestClient.Builder productionBuilder() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().requestFactory(factory);
    }

    public record Station(String code, BigDecimal latitude, BigDecimal longitude) {}
}
