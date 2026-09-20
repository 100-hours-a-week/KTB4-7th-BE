package com.memme.dto.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AddressSearchDtoTest {

    @Test
    void 주소_검색_요청_DTO는_정의된_쿼리_파라미터를_가진다() throws Exception {
        Class<?> requestType = Class.forName("com.memme.dto.store.AddressSearchRequest");

        assertTrue(requestType.isRecord());
        assertEquals(
                Arrays.asList("query", "cursor", "size"),
                Arrays.stream(requestType.getRecordComponents()).map(RecordComponent::getName).toList()
        );
    }

    @Test
    void 주소_검색_응답_DTO는_주소목록과_다음_커서를_가진다() throws Exception {
        Class<?> responseType = Class.forName("com.memme.dto.store.AddressSearchResponse");

        assertTrue(responseType.isRecord());
        assertEquals(
                Arrays.asList("addresses", "nextCursor"),
                Arrays.stream(responseType.getRecordComponents()).map(RecordComponent::getName).toList()
        );
    }
}
