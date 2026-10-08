package com.memme.controller.store;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.store.MenuImageUploadResponse;
import com.memme.dto.store.MenuListResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.exception.store.MenuRequestException;
import com.memme.service.store.MenuQueryService;
import com.memme.service.store.MenuUploadService;
import com.memme.service.store.MenuWriteService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MenuControllerTest {

    private final MenuQueryService query = mock(MenuQueryService.class);
    private final MenuWriteService writes = mock(MenuWriteService.class);
    private final MenuUploadService uploads = mock(MenuUploadService.class);

    @Test
    void 메뉴_목록은_저장_메뉴와_임시_배치를_구분해_반환한다() throws Exception {
        when(query.getMenuList(1L, 2L)).thenReturn(new MenuListResponse(4,
                List.of(new MenuListResponse.Item(27, 1, "아메리카노", 4000, "COFFEE",
                        OffsetDateTime.parse("2026-10-07T13:00:00+09:00"))),
                new MenuListResponse.DraftBatch(15, "PROCESSING",
                        OffsetDateTime.parse("2026-10-07T13:15:00+09:00"), null, null)));

        mvc().perform(get("/v2/menu-items").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuRevision").value(4))
                .andExpect(jsonPath("$.data.items[0].name").value("아메리카노"))
                .andExpect(jsonPath("$.data.draftBatch.status").value("PROCESSING"));
    }

    @Test
    void 세션이_없으면_메뉴를_조회하지_않는다() throws Exception {
        mvc().perform(get("/v2/menu-items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메뉴_행_검증_오류는_422와_행별_필드를_반환한다() throws Exception {
        when(writes.patch(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq("key"), any()))
                .thenThrow(new MenuRequestException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "입력값을 확인해 주세요.", Map.of("fieldErrors", List.of(Map.of(
                        "menuId", 27, "field", "price", "code", "OUT_OF_RANGE",
                        "message", "가격을 확인해 주세요.")))));

        mvc().perform(patch("/v2/menu-items").session(session())
                        .header("Idempotency-Key", "key")
                        .contentType("application/json")
                        .content("""
                                {"expectedMenuRevision":4,"items":[{"menuId":27,"name":"아메리카노","price":-1,"category":"COFFEE","order":1}]}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.data.fieldErrors[0].menuId").value(27))
                .andExpect(jsonPath("$.data.fieldErrors[0].field").value("price"));
    }

    @Test
    void 이미지_접수는_배치_ID와_PENDING_상태를_반환한다() throws Exception {
        when(uploads.upload(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq("upload-key"), any()))
                .thenReturn(new MenuImageUploadResponse(15, 1,
                        OffsetDateTime.parse("2026-10-07T13:15:00+09:00"), "MENU_LIST"));
        var image = new MockMultipartFile("images", "menu.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9});

        mvc().perform(multipart("/v2/menu-images").file(image).session(session())
                        .header("Idempotency-Key", "upload-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.data.batchId").value(15));
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new MenuController(query, writes, uploads))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE, new AuthenticatedUserSession(1L, 2L));
        return session;
    }
}
