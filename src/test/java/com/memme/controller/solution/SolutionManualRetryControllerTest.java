package com.memme.controller.solution;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.exception.solution.SolutionManualRetryException;
import com.memme.service.solution.SolutionManualRetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SolutionManualRetryControllerTest {

    private static final String PATH = "/v1/solutions/today/retries";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private SolutionManualRetryService retryService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void completedRetryReturnsBundleIdForSessionStore() throws Exception {
        when(retryService.retry(3L, 7L)).thenReturn(new SolutionManualRetryService.Result(
                SolutionManualRetryService.Status.COMPLETED, 81L));

        mvc.perform(post(PATH).sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                        new AuthenticatedUserSession(3L, 7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("솔루션 재시도가 완료되었습니다."))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.solutionBundleId").value(81));

        verify(retryService).retry(3L, 7L);
    }

    @Test
    void generatingRetryReturnsConflictAndBundleId() throws Exception {
        when(retryService.retry(3L, 7L)).thenReturn(new SolutionManualRetryService.Result(
                SolutionManualRetryService.Status.IN_PROGRESS, 81L));

        mvc.perform(post(PATH).sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                        new AuthenticatedUserSession(3L, 7L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.solutionBundleId").value(81));
    }

    @Test
    void retryWithoutFailedBundleReturnsConflictWithoutData() throws Exception {
        when(retryService.retry(3L, 7L)).thenThrow(new SolutionManualRetryException(
                SolutionManualRetryException.Reason.NOT_ALLOWED));

        mvc.perform(post(PATH).sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                        new AuthenticatedUserSession(3L, 7L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("NOT_ALLOWED"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void retryWithoutReadyDataReturnsUnprocessableContent() throws Exception {
        when(retryService.retry(3L, 7L)).thenThrow(new SolutionManualRetryException(
                SolutionManualRetryException.Reason.DATA_NOT_READY));

        mvc.perform(post(PATH).sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                        new AuthenticatedUserSession(3L, 7L)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value("DATA_NOT_READY"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void retryGenerationFailureReturnsServerErrorWithoutBundleId() throws Exception {
        when(retryService.retry(3L, 7L)).thenReturn(new SolutionManualRetryService.Result(
                SolutionManualRetryService.Status.FAILED, 81L));

        mvc.perform(post(PATH).sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                        new AuthenticatedUserSession(3L, 7L)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("솔루션 재시도에 실패했습니다. 잠시 후 다시 시도해주세요."))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void missingSessionReturnsUnauthorizedBeforeCallingService() throws Exception {
        mvc.perform(post(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        verify(retryService, never()).retry(3L, 7L);
    }

    @Test
    void anotherStoreOwnerReturnsRetrySpecificForbiddenMessage() throws Exception {
        when(retryService.retry(3L, 7L)).thenThrow(new SolutionManualRetryException(
                SolutionManualRetryException.Reason.STORE_OWNER_REQUIRED));

        mvc.perform(post(PATH).sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                        new AuthenticatedUserSession(3L, 7L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("본인 매장의 솔루션만 다시 생성할 수 있습니다."))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
