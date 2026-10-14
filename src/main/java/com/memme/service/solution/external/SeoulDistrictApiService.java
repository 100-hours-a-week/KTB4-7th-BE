package com.memme.service.solution.external;

import com.memme.dto.solution.external.SeoulEstimatedSalesResponse;
import com.memme.dto.solution.external.SeoulFootTrafficResponse;
import com.memme.dto.solution.external.SeoulStoreCountResponse;
import com.memme.exception.solution.ExternalDataLookupException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class SeoulDistrictApiService {

    private static final String BASE_URL = "http://openapi.seoul.go.kr:8088";
    private static final int MAX_ROWS = 1000;

    private final RestClient restClient;
    private final String apiKey;
    private final SeoulDistrictResponseService responseService;

    @Autowired
    public SeoulDistrictApiService(
            @Value("${SEOUL_OPEN_DATA_API_KEY:}") String apiKey,
            SeoulDistrictResponseService responseService) {
        this(productionBuilder(), apiKey, responseService);
    }

    SeoulDistrictApiService(RestClient.Builder builder, String apiKey, SeoulDistrictResponseService responseService) {
        this.restClient = builder.baseUrl(BASE_URL).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.responseService = responseService;
    }

    public Optional<SeoulEstimatedSalesResponse> estimatedSales(int start, int end, String yearQuarter) {
        return responseService.estimatedSales(fetch("VwsmAdstrdSelngW", start, end, yearQuarter));
    }

    public Optional<SeoulStoreCountResponse> storeCount(int start, int end, String yearQuarter) {
        return responseService.storeCount(fetch("VwsmAdstrdStorW", start, end, yearQuarter));
    }

    public Optional<SeoulFootTrafficResponse> footTraffic(int start, int end) {
        return responseService.footTraffic(fetch("VwsmAdstrdFlpopW", start, end, null));
    }

    private String fetch(String serviceName, int start, int end, String yearQuarter) {
        if (apiKey.isBlank()) {
            throw new ExternalDataLookupException("SEOUL_KEY_MISSING");
        }
        if (!apiKey.matches("[A-Za-z0-9]+")) {
            throw new ExternalDataLookupException("SEOUL_KEY_INVALID");
        }
        if (start < 1 || end < start || (long) end - start + 1 > MAX_ROWS) {
            throw new IllegalArgumentException("Seoul API page must contain 1 to 1000 rows");
        }
        if (yearQuarter != null && !yearQuarter.matches("[0-9]{4}[1-4]")) {
            throw new IllegalArgumentException("Invalid year-quarter code");
        }

        String path = "/" + apiKey + "/json/" + serviceName + "/" + start + "/" + end;
        if (yearQuarter != null) {
            path += "/" + yearQuarter;
        }
        URI uri = URI.create(BASE_URL + path);
        try {
            return restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ExternalDataLookupException(status == 401 || status == 403
                    ? "SEOUL_AUTH_ERROR" : "SEOUL_HTTP_ERROR");
        } catch (ResourceAccessException exception) {
            throw new ExternalDataLookupException(isTimeout(exception)
                    ? "SEOUL_TIMEOUT" : "SEOUL_CONNECTION_ERROR");
        } catch (RestClientException exception) {
            throw new ExternalDataLookupException("SEOUL_INVALID_RESPONSE");
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

    private static RestClient.Builder productionBuilder() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().requestFactory(factory);
    }
}
