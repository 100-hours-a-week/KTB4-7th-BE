package com.memme.service.solution.external;

import com.memme.dto.solution.external.SeoulEstimatedSalesResponse;
import com.memme.dto.solution.external.SeoulFootTrafficResponse;
import com.memme.dto.solution.external.SeoulStoreCountResponse;
import com.memme.exception.solution.ExternalDataLookupException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SeoulDistrictResponseService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public Optional<SeoulEstimatedSalesResponse> estimatedSales(String rawResponse) {
        return parse(rawResponse, "VwsmAdstrdSelngW", SeoulEstimatedSalesResponse.class);
    }

    public Optional<SeoulStoreCountResponse> storeCount(String rawResponse) {
        return parse(rawResponse, "VwsmAdstrdStorW", SeoulStoreCountResponse.class);
    }

    public Optional<SeoulFootTrafficResponse> footTraffic(String rawResponse) {
        return parse(rawResponse, "VwsmAdstrdFlpopW", SeoulFootTrafficResponse.class);
    }

    private <T> Optional<T> parse(String rawResponse, String serviceName, Class<T> responseType) {
        JsonNode root;
        try {
            root = JSON.readTree(rawResponse);
        } catch (Exception exception) {
            throw new ExternalDataLookupException("SEOUL_INVALID_RESPONSE");
        }
        if (root == null || !root.isObject()) {
            throw new ExternalDataLookupException("SEOUL_INVALID_RESPONSE");
        }
        JsonNode service = root.path(serviceName);
        JsonNode result = service.isObject() ? service.path("RESULT") : root.path("RESULT");
        String code = result.path("CODE").asString();
        if ("INFO-200".equals(code)) {
            return Optional.empty();
        }
        if ("INFO-100".equals(code)) {
            throw new ExternalDataLookupException("SEOUL_AUTH_ERROR");
        }
        if (code.isBlank()) {
            throw new ExternalDataLookupException("SEOUL_INVALID_RESPONSE");
        }
        if (!"INFO-000".equals(code)) {
            throw new ExternalDataLookupException("SEOUL_PROVIDER_ERROR");
        }
        JsonNode count = service.path("list_total_count");
        JsonNode rows = service.path("row");
        if (!count.canConvertToInt() || count.intValue() < 0 || !rows.isArray()) {
            throw new ExternalDataLookupException("SEOUL_INVALID_RESPONSE");
        }
        if (count.intValue() == 0) {
            return Optional.empty();
        }
        if (rows.isEmpty()) {
            throw new ExternalDataLookupException("SEOUL_INVALID_RESPONSE");
        }
        try {
            return Optional.of(JSON.readValue(rawResponse, responseType));
        } catch (Exception exception) {
            throw new ExternalDataLookupException("SEOUL_INVALID_RESPONSE");
        }
    }
}
