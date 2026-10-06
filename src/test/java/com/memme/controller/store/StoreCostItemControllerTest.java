package com.memme.controller.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.store.StoreCostItemGetData;
import com.memme.dto.store.StoreCostItemPutData;
import com.memme.dto.store.StoreCostItemPutResult;
import com.memme.dto.store.StoreCostItemRequest;
import com.memme.dto.store.StoreCostItemResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.store.StoreCostItemService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StoreCostItemControllerTest {

    @Test
    void 저장된_비용이_없어도_조회는_200이다() {
        StoreCostItemService service = mock(StoreCostItemService.class);
        when(service.get(1L, 2L, "2026-10")).thenReturn(new StoreCostItemGetData(null));

        var response = new StoreCostItemController(service).get(authenticatedRequest(), "2026-10");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().costItem()).isNull();
    }

    @Test
    void 신규_저장은_201이고_기존_월_수정은_200이다() {
        StoreCostItemService service = mock(StoreCostItemService.class);
        StoreCostItemRequest body = new StoreCostItemRequest(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        StoreCostItemResponse item = new StoreCostItemResponse("2026-10", 0, 0, BigDecimal.ZERO,
                OffsetDateTime.parse("2026-10-06T15:00:00+09:00"));
        when(service.put(1L, 2L, "2026-10", body))
                .thenReturn(new StoreCostItemPutResult(new StoreCostItemPutData(item), true))
                .thenReturn(new StoreCostItemPutResult(new StoreCostItemPutData(item), false));
        StoreCostItemController controller = new StoreCostItemController(service);

        assertThat(controller.put(authenticatedRequest(), "2026-10", body).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(controller.put(authenticatedRequest(), "2026-10", body).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void 세션이_없으면_인증_예외를_던진다() {
        StoreCostItemController controller = new StoreCostItemController(mock(StoreCostItemService.class));
        assertThatThrownBy(() -> controller.get(new MockHttpServletRequest(), "2026-10"))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void 원가율이_범위를_벗어나면_HTTP_400과_필드_오류를_반환한다() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(
                new StoreCostItemController(mock(StoreCostItemService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        MockHttpSession session = (MockHttpSession) authenticatedRequest().getSession();

        mvc.perform(put("/v2/stores/me/cost-items/2026-10")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rentAmount\":0,\"laborAmount\":0,\"ingredientCostRate\":1.1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("ingredientCostRate"))
                .andExpect(jsonPath("$.data.fieldErrors[0].code").value("OUT_OF_RANGE"));
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(1L, 2L));
        request.setSession(session);
        return request;
    }
}
