package com.memme.dto.sales;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SalesAvailableMonthsResponseTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesAvailableMonthsUsingMonthsField() throws Exception {
        var response = new SalesAvailableMonthsResponse(List.of("2026-01", "2026-02"));

        var json = mapper.readTree(mapper.writeValueAsString(response));

        assertThat(json.path("months").valueStream().map(node -> node.asString()).toList())
                .containsExactly("2026-01", "2026-02");
    }
}
