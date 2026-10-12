package com.memme.controller.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.store.MenuBatchResponse;
import com.memme.dto.store.MenuConfirmationResponse;
import com.memme.dto.store.MenuImageUploadResponse;
import com.memme.dto.store.MenuListResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.exception.store.MenuRequestException;
import com.memme.service.store.MenuQueryService;
import com.memme.service.store.MenuWriteService;
import com.memme.service.store.upload.MenuUploadException;
import com.memme.service.store.upload.MenuUploadFile;
import com.memme.service.store.upload.MenuUploadResult;
import com.memme.service.store.upload.MenuUploadService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MenuControllerTest {

    private final MenuUploadService uploads = mock(MenuUploadService.class);
    private final MenuQueryService queries = mock(MenuQueryService.class);
    private final MenuWriteService writes = mock(MenuWriteService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new MenuController(uploads, queries, writes))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void 이미지_업로드는_최종_완료_상태와_배치_ID를_반환한다() throws Exception {
        when(uploads.upload(eq(1L), eq("upload-key"), any())).thenReturn(
                new MenuUploadResult("COMPLETED", new MenuImageUploadResponse(15, 1,
                        OffsetDateTime.parse("2026-10-13T10:00:00+09:00"), null, "MENU_LIST")));
        byte[] bytes = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};
        MockMultipartFile image = new MockMultipartFile("images", "board.jpg", "image/jpeg", bytes);

        mvc.perform(multipart("/v2/menu-images").file(image).session(session())
                        .header("Idempotency-Key", "upload-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.batchId").value(15))
                .andExpect(jsonPath("$.data.failReason").isEmpty());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MenuUploadFile>> files = ArgumentCaptor.forClass(List.class);
        verify(uploads).upload(eq(1L), eq("upload-key"), files.capture());
        assertThat(files.getValue()).hasSize(1);
        assertThat(files.getValue().getFirst().content()).isEqualTo(bytes);
    }

    @Test
    void AI_실패는_HTTP_200과_FAILED_사유를_반환한다() throws Exception {
        when(uploads.upload(eq(1L), eq("upload-key"), any())).thenReturn(
                new MenuUploadResult("FAILED", new MenuImageUploadResponse(15, 1,
                        OffsetDateTime.parse("2026-10-13T10:00:00+09:00"),
                        "LOW_IMAGE_QUALITY", "MENU_LIST")));
        MockMultipartFile image = new MockMultipartFile("images", "board.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        mvc.perform(multipart("/v2/menu-images").file(image).session(session())
                        .header("Idempotency-Key", "upload-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failReason").value("LOW_IMAGE_QUALITY"));
    }

    @Test
    void 파일_오류는_상태_코드와_파일_위치를_반환한다() throws Exception {
        when(uploads.upload(eq(1L), eq("key"), any()))
                .thenThrow(new MenuUploadException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "UNSUPPORTED_IMAGE_TYPE", 0, "JPG 또는 PNG 이미지만 업로드할 수 있습니다."));

        mvc.perform(multipart("/v2/menu-images").file(new MockMultipartFile(
                        "images", "board.gif", "image/gif", new byte[] {1}))
                        .session(session()).header("Idempotency-Key", "key"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.data.code").value("UNSUPPORTED_IMAGE_TYPE"))
                .andExpect(jsonPath("$.data.fileIndex").value(0));
    }

    @Test
    void 메뉴_목록과_분석_상태를_구분해_반환한다() throws Exception {
        when(queries.getMenuList(1L, 2L)).thenReturn(new MenuListResponse(4,
                List.of(new MenuListResponse.Item(27, 1, "아메리카노", 4000, "COFFEE",
                        OffsetDateTime.parse("2026-10-13T09:00:00+09:00"))),
                new MenuListResponse.DraftBatch(15, "PROCESSING",
                        OffsetDateTime.parse("2026-10-13T10:00:00+09:00"), null, null)));
        when(queries.getBatch(1L, 2L, 15L)).thenReturn(new MenuQueryService.BatchResult(
                "PROCESSING", new MenuBatchResponse(15, 4,
                        OffsetDateTime.parse("2026-10-13T10:00:00+09:00"),
                        null, null, List.of())));

        mvc.perform(get("/v2/menu-items").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("아메리카노"))
                .andExpect(jsonPath("$.data.draftBatch.status").value("PROCESSING"));
        mvc.perform(get("/v2/menu-image-batches/15").session(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.detectedItems").isEmpty());
    }

    @Test
    void 세션이_없으면_조회와_업로드를_막는다() throws Exception {
        mvc.perform(get("/v2/menu-items")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/v2/menu-images").file(new MockMultipartFile(
                "images", "board.jpg", "image/jpeg", new byte[] {1})))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메뉴_검증_오류는_422와_행별_오류를_반환한다() throws Exception {
        when(writes.patch(eq(1L), eq(2L), eq("key"), any()))
                .thenThrow(new MenuRequestException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "입력값을 확인해 주세요.", Map.of("fieldErrors", List.of(Map.of(
                        "menuId", 27, "field", "price", "code", "OUT_OF_RANGE",
                        "message", "가격을 확인해 주세요.")))));

        mvc.perform(patch("/v2/menu-items").session(session())
                        .header("Idempotency-Key", "key")
                        .contentType("application/json")
                        .content("""
                                {"expectedMenuRevision":4,"items":[{"menuId":27,"name":"아메리카노",
                                "price":-1,"category":"COFFEE","order":1}]}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.data.fieldErrors[0].menuId").value(27))
                .andExpect(jsonPath("$.data.fieldErrors[0].code").value("OUT_OF_RANGE"));
    }

    @Test
    void 후보_확정은_배치_ID와_저장된_메뉴를_반환한다() throws Exception {
        when(writes.confirm(eq(1L), eq(2L), eq(15L), eq("confirm-key"), any()))
                .thenReturn(new MenuConfirmationResponse(15, 5,
                        OffsetDateTime.parse("2026-10-13T10:05:00+09:00"),
                        List.of(new MenuConfirmationResponse.Item(101, 31,
                                "아메리카노", 4000, "COFFEE"))));

        mvc.perform(post("/v2/menu-image-batches/15/confirmations").session(session())
                        .header("Idempotency-Key", "confirm-key")
                        .contentType("application/json")
                        .content("""
                                {"expectedMenuRevision":4,"items":[{"itemId":101,"name":"아메리카노",
                                "price":4000,"category":"COFFEE","order":1}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value(15))
                .andExpect(jsonPath("$.data.menuRevision").value(5))
                .andExpect(jsonPath("$.data.items[0].menuId").value(31));
    }

    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                new AuthenticatedUserSession(1L, 2L));
        return session;
    }
}
