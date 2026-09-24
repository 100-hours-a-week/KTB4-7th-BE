package com.memme.controller.store;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.dto.store.AddressSearchRequest;
import com.memme.dto.store.AddressSearchResponse;
import com.memme.exception.store.AddressSearchFailedException;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.store.AddressSearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AddressSearchControllerTest {

    private AddressSearchService addressSearchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        addressSearchService = mock(AddressSearchService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new AddressSearchController(addressSearchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void 주소_검색_요청을_처리하고_200_응답을_반환한다() throws Exception {
        when(addressSearchService.search(new AddressSearchRequest("판교역로", "2", 5)))
                .thenReturn(new AddressSearchResponse(
                        List.of(new AddressSearchResponse.Address(
                                "13494", "경기도 성남시 분당구 판교역로 166", "경기도 성남시 분당구 백현동 532"
                        )),
                        "3"
                ));

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/addresses/search")
                        .queryParam("query", "판교역로")
                        .queryParam("cursor", "2")
                        .queryParam("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.message").value("조회에 성공했습니다."))
                .andExpect(jsonPath(
                        "$.data.addresses[0].postalCode").value("13494"))
                .andExpect(jsonPath(
                        "$.data.nextCursor").value("3"));

        verify(addressSearchService).search(new AddressSearchRequest("판교역로", "2", 5));
    }

    @Test
    void 빈_주소_검색어는_400_fieldErrors로_반환한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/addresses/search")
                        .queryParam("query", ""))
                .andExpect(status()
                        .isBadRequest())
                .andExpect(jsonPath(
                        "$.data.fieldErrors[?(@.field == 'query')]").exists());

        verifyNoInteractions(addressSearchService);
    }

    @Test
    void 잘못된_cursor는_400_fieldErrors로_반환한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/addresses/search")
                        .queryParam("query", "판교역로")
                        .queryParam("cursor", "0"))
                .andExpect(status()
                        .isBadRequest())
                .andExpect(jsonPath(
                        "$.data.fieldErrors[?(@.field == 'cursor')]").exists());

        verifyNoInteractions(addressSearchService);
    }

    @Test
    void 잘못된_size는_400_fieldErrors로_반환한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/addresses/search")
                        .queryParam("query", "판교역로")
                        .queryParam("size", "11"))
                .andExpect(status()
                        .isBadRequest())
                .andExpect(jsonPath(
                        "$.data.fieldErrors[?(@.field == 'size')]").exists());

        verifyNoInteractions(addressSearchService);
    }

    @Test
    void 숫자가_아닌_size는_400_공통_응답으로_반환한다() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/v1/addresses/search")
                        .queryParam("query", "판교역로")
                        .queryParam("size", "abc"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.data").doesNotExist());

        verifyNoInteractions(addressSearchService);
    }

    @Test
    void 주소_검색_외부_API_실패는_502_응답으로_반환한다() throws Exception {
        when(addressSearchService.search(new AddressSearchRequest("판교역로", null, null)))
                .thenThrow(new AddressSearchFailedException());

        mockMvc.perform(MockMvcRequestBuilders.get("/v1/addresses/search")
                        .queryParam("query", "판교역로"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath(
                        "$.message").value("주소 검색 서비스를 이용할 수 없습니다. 다시 시도해주세요."))
                .andExpect(jsonPath(
                        "$.data").doesNotExist());
    }
}
