package com.memme.controller.sales;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.sales.profit.SalesUploadCostEntryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SalesUploadCostEntryControllerTest {

    private SalesUploadCostEntryService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(SalesUploadCostEntryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SalesUploadCostEntryController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void 세션이_없으면_비용을_조회할_수_없다() throws Exception {
        mvc.perform(get("/v2/sales/uploads/3/cost-items"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void 여러_달_중_하나라도_필수값이_없으면_월별_오류를_반환한다() throws Exception {
        mvc.perform(put("/v2/sales/uploads/3/cost-items")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                  {"costMonth":"2026-08","rentAmount":1000,"laborAmount":2000,"ingredientCostRate":40},
                                  {"costMonth":"2026-09","rentAmount":null,"laborAmount":2000,"ingredientCostRate":40}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].costMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("rentAmount"))
                .andExpect(jsonPath("$.data.fieldErrors[0].code").value("REQUIRED"));
        verifyNoInteractions(service);
    }

    @Test
    void 원가율_소수점_둘째_자리는_거부한다() throws Exception {
        mvc.perform(put("/v2/sales/uploads/3/cost-items")
                        .session(authenticatedSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"costMonth":"2026-09","rentAmount":1000,
                                "laborAmount":2000,"ingredientCostRate":35.55}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors[0].costMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("ingredientCostRate"))
                .andExpect(jsonPath("$.data.fieldErrors[0].message")
                        .value("원가율은 소수점 첫째 자리까지 입력할 수 있습니다."));
        verifyNoInteractions(service);
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(1L, 2L));
        return session;
    }
}
