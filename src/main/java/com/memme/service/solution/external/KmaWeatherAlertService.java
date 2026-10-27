package com.memme.service.solution.external;

import com.memme.exception.solution.ExternalDataLookupException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class KmaWeatherAlertService {

    private static final String BASE_URL = "https://apihub.kma.go.kr";
    private static final String PATH = "/api/typ01/url/wrn_now_data.php";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KMA_DATE_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmm")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final int REQUIRED_FIELD_COUNT = 9;

    private final RestClient restClient;
    private final String authKey;
    private final KmaWeatherAlertAreaMapper areaMapper;
    private final Clock clock;

    @Autowired
    public KmaWeatherAlertService(@Value("${KMA_API_HUB_AUTH_KEY:}") String authKey) {
        this(productionBuilder(), authKey, new KmaWeatherAlertAreaMapper(), Clock.system(KOREA));
    }

    KmaWeatherAlertService(RestClient.Builder builder, String authKey, Clock clock) {
        this(builder, authKey, new KmaWeatherAlertAreaMapper(), clock);
    }

    KmaWeatherAlertService(RestClient.Builder builder, String authKey,
                           KmaWeatherAlertAreaMapper areaMapper, Clock clock) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.authKey = authKey == null ? "" : authKey.trim();
        this.areaMapper = areaMapper;
        this.clock = clock;
    }

    public List<KmaWeatherAlert> findActiveLandAlerts(String storeAddress) {
        if (authKey.isBlank()) {
            throw new ExternalDataLookupException("KMA_ALERT_KEY_MISSING");
        }
        Set<String> applicableAreaCodes = areaMapper.applicableAreaCodes(storeAddress);
        List<AlertRow> rows = parseResponse(fetchCurrentStatus());
        LocalDateTime now = LocalDateTime.now(clock.withZone(KOREA));
        Map<String, KmaWeatherAlert> uniqueAlerts = new LinkedHashMap<>();
        for (AlertRow row : rows) {
            if (!row.areaCode().startsWith("L")) {
                continue;
            }
            if (!areaMapper.isKnownLandArea(row.areaCode())) {
                throw new ExternalDataLookupException("KMA_ALERT_INVALID_RESPONSE");
            }
            if (!applicableAreaCodes.contains(row.areaCode()) || isPreliminary(row.level())
                    || isReleasedOrNotEffective(row.command())) {
                continue;
            }

            LocalDateTime effectiveAt = parseEffectiveAt(row.effectiveAt());
            if (effectiveAt.isAfter(now)) {
                continue;
            }
            String name = alertName(row.warning(), row.level());
            uniqueAlerts.putIfAbsent(name, new KmaWeatherAlert(name, row.areaCode(), effectiveAt));
        }
        return List.copyOf(uniqueAlerts.values());
    }

    private String fetchCurrentStatus() {
        URI uri;
        try {
            // APIHub keys are supplied URL-encoded; preserve the supplied encoding to avoid double-encoding.
            uri = URI.create(BASE_URL + PATH + "?fe=e&tm=&disp=0&help=0&authKey=" + authKey);
        } catch (IllegalArgumentException exception) {
            throw new ExternalDataLookupException("KMA_ALERT_KEY_INVALID");
        }

        try {
            var response = restClient.get().uri(uri).retrieve().toEntity(byte[].class);
            byte[] body = response.getBody();
            if (body == null) {
                throw new ExternalDataLookupException("KMA_ALERT_INVALID_RESPONSE");
            }
            return decodeResponse(body, response.getHeaders().getContentType());
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ExternalDataLookupException(status == 401 || status == 403
                    ? "KMA_ALERT_AUTH_ERROR" : "KMA_ALERT_HTTP_ERROR");
        } catch (ResourceAccessException exception) {
            throw new ExternalDataLookupException(isTimeout(exception)
                    ? "KMA_ALERT_TIMEOUT" : "KMA_ALERT_CONNECTION_ERROR");
        } catch (RestClientException exception) {
            throw new ExternalDataLookupException("KMA_ALERT_INVALID_RESPONSE");
        }
    }

    private String decodeResponse(byte[] body, MediaType contentType) {
        Charset declaredCharset = contentType == null ? null : contentType.getCharset();
        if (declaredCharset != null) {
            return new String(body, declaredCharset);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString();
        } catch (CharacterCodingException exception) {
            // Older KMA text endpoints may omit charset while returning Korean Windows-949 bytes.
            return new String(body, Charset.forName("MS949"));
        }
    }

    private List<AlertRow> parseResponse(String response) {
        String normalized = response.replace("\uFEFF", "").replace("\r", "");
        if (isAuthenticationError(normalized)) {
            throw new ExternalDataLookupException("KMA_ALERT_AUTH_ERROR");
        }
        if (normalized.toUpperCase().contains("ERROR")) {
            throw new ExternalDataLookupException("KMA_ALERT_PROVIDER_ERROR");
        }
        if (!normalized.contains("#START7777") || !normalized.contains("#7777END")) {
            throw new ExternalDataLookupException("KMA_ALERT_INVALID_RESPONSE");
        }

        List<AlertRow> rows = new ArrayList<>();
        for (String line : normalized.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            String[] fields = trimmed.split(",", -1);
            if (fields.length < REQUIRED_FIELD_COUNT) {
                throw new ExternalDataLookupException("KMA_ALERT_INVALID_RESPONSE");
            }
            String areaCode = fields[2].trim();
            String effectiveAt = fields[5].trim();
            String warning = fields[6].trim();
            String level = fields[7].trim();
            String command = fields[8].trim();
            if (areaCode.isBlank() || fields[4].isBlank() || effectiveAt.isBlank()
                    || warning.isBlank() || level.isBlank() || command.isBlank()) {
                throw new ExternalDataLookupException("KMA_ALERT_INVALID_RESPONSE");
            }
            rows.add(new AlertRow(areaCode, effectiveAt, warning, level, command));
        }
        return List.copyOf(rows);
    }

    private LocalDateTime parseEffectiveAt(String value) {
        try {
            return LocalDateTime.parse(value, KMA_DATE_TIME);
        } catch (DateTimeParseException exception) {
            throw new ExternalDataLookupException("KMA_ALERT_INVALID_RESPONSE");
        }
    }

    private String alertName(String warning, String level) {
        String suffix = switch (level) {
            case "주의", "주의보" -> "주의보";
            case "경보" -> "경보";
            default -> throw new ExternalDataLookupException("KMA_ALERT_INVALID_RESPONSE");
        };
        return warning.endsWith(suffix) ? warning : warning + suffix;
    }

    private boolean isPreliminary(String level) {
        return level.equals("예비") || level.equals("예비특보");
    }

    private boolean isReleasedOrNotEffective(String command) {
        return command.contains("해제") || command.contains("미발효");
    }

    private boolean isAuthenticationError(String response) {
        String upperCase = response.toUpperCase();
        return upperCase.contains("SERVICE_KEY_IS_NOT_REGISTERED")
                || upperCase.contains("AUTH_KEY")
                || upperCase.contains("AUTH ERROR")
                || response.contains("인증키 오류");
    }

    private boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static RestClient.Builder productionBuilder() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().requestFactory(factory);
    }

    private record AlertRow(String areaCode, String effectiveAt, String warning, String level, String command) {
    }
}
