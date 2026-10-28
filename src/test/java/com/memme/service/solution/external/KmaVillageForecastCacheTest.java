package com.memme.service.solution.external;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.memme.service.cache.SharedJsonCache;
import com.memme.service.store.StoreCoordinates;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KmaVillageForecastCacheTest {
    private final Map<String, String> saved = new HashMap<>();
    private final StoreCoordinates coordinates = new StoreCoordinates(new BigDecimal("37.4786"), new BigDecimal("126.8646"));

    private SharedJsonCache cache() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> saved.get(call.getArgument(0)));
        doAnswer(call -> { saved.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), any(Duration.class));
        return new SharedJsonCache(redis, true, "test:cache:v1");
    }

    @Test
    void sharesOneGridIssueAcrossServiceInstancesAndChangesKeyAtPublicationBoundary() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getRawQuery()).contains("base_time=1100"))
                .andRespond(withSuccess(payload(), MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(request.getURI().getRawQuery()).contains("base_time=1400"))
                .andRespond(withSuccess(payload(), MediaType.APPLICATION_JSON));
        var first = new KmaVillageForecastService(builder, "test-key", at("2026-10-28T04:00:00Z"), cache());
        var second = new KmaVillageForecastService(builder, "test-key", at("2026-10-28T05:09:00Z"), cache());
        var nextIssue = new KmaVillageForecastService(builder, "test-key", at("2026-10-28T05:10:00Z"), cache());
        assertThat(second.fetch(coordinates)).isEqualTo(first.fetch(coordinates));
        nextIssue.fetch(coordinates);
        assertThat(saved).hasSize(2);
        server.verify();
    }

    @Test
    void emptyForecastIsNotCached() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {}).andRespond(withSuccess(
                "{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"totalCount\":0}}}", MediaType.APPLICATION_JSON));
        var service = new KmaVillageForecastService(builder, "test-key", at("2026-10-28T04:00:00Z"), cache());
        assertThat(service.fetch(coordinates)).isEmpty();
        assertThat(saved).isEmpty();
    }

    private Clock at(String instant) { return Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul")); }
    private String payload() {
        return """
                {"response":{"header":{"resultCode":"00"},"body":{"totalCount":1,
                "items":{"item":[{"baseDate":"20261028","baseTime":"1100","category":"POP",
                "fcstDate":"20261028","fcstTime":"1500","fcstValue":"60","nx":58,"ny":125}]}}}}
                """;
    }
}
