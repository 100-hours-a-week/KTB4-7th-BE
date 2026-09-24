package com.memme.controller.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.common.ApiResponse;
import com.memme.dto.store.StoreProfileResponse;
import com.memme.dto.store.StoreProfileUpdateRequest;
import com.memme.dto.store.StoreProfileUpdateResponse;
import com.memme.exception.auth.AuthenticationRequiredException;
import com.memme.service.store.StoreProfileService;
import com.memme.service.store.StoreProfileUpdateService;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class StoreControllerTest {

    @Test
    void 로그인한_사용자의_매장_정보를_조회하고_200을_반환한다() {
        StoreProfileService storeProfileService = mock(StoreProfileService.class);
        StoreProfileUpdateService storeProfileUpdateService = mock(StoreProfileUpdateService.class);
        StoreController storeController = new StoreController(storeProfileService, storeProfileUpdateService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(1L, 10L));
        request.setSession(session);
        when(storeProfileService.getProfile(1L)).thenReturn(storeProfileResponse());

        ResponseEntity<ApiResponse<StoreProfileResponse>> response = storeController.getProfile(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("조회에 성공했습니다.", response.getBody().message());
        assertEquals("맴매카페", response.getBody().data().store().storeName());
        verify(storeProfileService).getProfile(1L);
    }

    @Test
    void 매장_정보_조회에_인증_세션이_없으면_인증_예외를_던진다() {
        StoreProfileService storeProfileService = mock(StoreProfileService.class);
        StoreProfileUpdateService storeProfileUpdateService = mock(StoreProfileUpdateService.class);
        StoreController storeController = new StoreController(storeProfileService, storeProfileUpdateService);

        assertThrows(AuthenticationRequiredException.class, () -> storeController.getProfile(new MockHttpServletRequest()));
    }

    @Test
    void 로그인한_사용자의_매장_정보를_수정하고_200을_반환한다() {
        StoreProfileService storeProfileService = mock(StoreProfileService.class);
        StoreProfileUpdateService storeProfileUpdateService = mock(StoreProfileUpdateService.class);
        StoreController storeController = new StoreController(storeProfileService, storeProfileUpdateService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(1L, 10L));
        request.setSession(session);
        StoreProfileUpdateRequest updateRequest = new StoreProfileUpdateRequest("수정된 맴매카페", null, null);
        when(storeProfileUpdateService.updateProfile(1L, updateRequest)).thenReturn(storeProfileUpdateResponse());

        ResponseEntity<ApiResponse<StoreProfileUpdateResponse>> response = storeController.updateProfile(
                request, updateRequest
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("수정되었습니다.", response.getBody().message());
        assertEquals("수정된 맴매카페", response.getBody().data().store().storeName());
        verify(storeProfileUpdateService).updateProfile(1L, updateRequest);
    }

    @Test
    void 매장_정보_수정에_인증_세션이_없으면_인증_예외를_던진다() {
        StoreProfileService storeProfileService = mock(StoreProfileService.class);
        StoreProfileUpdateService storeProfileUpdateService = mock(StoreProfileUpdateService.class);
        StoreController storeController = new StoreController(storeProfileService, storeProfileUpdateService);
        StoreProfileUpdateRequest updateRequest = new StoreProfileUpdateRequest("수정된 맴매카페", null, null);

        assertThrows(
                AuthenticationRequiredException.class,
                () -> storeController.updateProfile(new MockHttpServletRequest(), updateRequest)
        );
    }

    private StoreProfileResponse storeProfileResponse() {
        return new StoreProfileResponse(new StoreProfileResponse.Store(
                10L,
                "123-45-67890",
                "맴매카페",
                new StoreProfileResponse.Address("06236", "서울특별시 강남구 테헤란로 123", "101호"),
                List.of(new StoreProfileResponse.BusinessHours(
                        DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(18, 0)
                ))
        ));
    }

    private StoreProfileUpdateResponse storeProfileUpdateResponse() {
        return new StoreProfileUpdateResponse(new StoreProfileUpdateResponse.Store(
                10L,
                "수정된 맴매카페",
                new StoreProfileUpdateResponse.Address("06236", "서울특별시 강남구 테헤란로 123", "101호"),
                List.of(new StoreProfileUpdateResponse.BusinessHours(
                        DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(18, 0)
                ))
        ));
    }
}
