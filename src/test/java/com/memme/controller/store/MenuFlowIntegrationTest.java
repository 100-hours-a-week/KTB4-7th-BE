package com.memme.controller.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.entity.auth.User;
import com.memme.entity.store.Store;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.store.MenuImageRepository;
import com.memme.repository.store.MenuRepository;
import com.memme.repository.store.MenuUploadBatchRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.store.recognition.MenuRecognitionAiClient;
import com.memme.service.store.recognition.MenuRecognitionAiException;
import com.memme.service.store.recognition.MenuRecognitionRequest;
import com.memme.service.store.recognition.MenuRecognitionResponse;
import com.memme.service.store.storage.MenuImageStorage;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class MenuFlowIntegrationTest {

    @Autowired private MenuController controller;
    @Autowired private GlobalExceptionHandler globalExceptions;
    @Autowired private UserRepository users;
    @Autowired private StoreRepository stores;
    @Autowired private MenuUploadBatchRepository batches;
    @Autowired private MenuImageRepository images;
    @Autowired private MenuRepository menus;
    @MockitoBean private MenuImageStorage storage;
    @MockitoBean private MenuRecognitionAiClient ai;

    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;
    private MockHttpSession session;
    private long storeId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString();
        User owner = users.save(User.create(unique + "@example.com", "encoded-password",
                "010" + unique.replace("-", "").substring(0, 8), LocalDateTime.now()));
        Store store = stores.save(Store.create(owner, null, null, "메뉴 통합 테스트", "06236",
                "서울특별시 강남구 테헤란로 123", null, LocalDateTime.now()));
        storeId = store.getId();
        session = new MockHttpSession();
        session.setAttribute(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                new AuthenticatedUserSession(owner.getId(), storeId));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(globalExceptions).build();
        when(storage.store(any())).thenAnswer(invocation -> "menu/" + UUID.randomUUID() + ".jpg");
        when(storage.temporaryReadUrl(any())).thenAnswer(invocation -> URI.create(
                "https://images.example.test/" + invocation.getArgument(0, String.class)));
    }

    @Test
    void 열_장_업로드부터_후보_확정까지_영속화하고_멱등_재요청은_AI를_다시_호출하지_않는다() throws Exception {
        when(ai.recognize(any())).thenAnswer(invocation -> {
            MenuRecognitionRequest request = invocation.getArgument(0);
            assertThat(request.storeId()).isEqualTo(storeId);
            assertThat(request.images()).hasSize(10);
            assertThat(request.images()).extracting(MenuRecognitionRequest.Image::order)
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            assertThat(request.images()).allSatisfy(image ->
                    assertThat(image.imageUrl()).startsWith("https://images.example.test/menu/"));
            return new MenuRecognitionResponse("ok", new MenuRecognitionResponse.Data(List.of(
                    detected(request.images().get(0).imageId(), "아메리카노", 4000),
                    detected(request.images().get(1).imageId(), "카페라떼", 4500))));
        });

        var upload = multipart("/v2/menu-images").session(session).header("Idempotency-Key", "upload-10");
        for (int index = 0; index < 10; index++) {
            upload.file(image(index));
        }
        String uploadBody = mvc.perform(upload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.imageCount").value(10))
                .andReturn().getResponse().getContentAsString();
        long batchId = json.readTree(uploadBody).path("data").path("batchId").asLong();
        assertThat(images.findAllByBatchIdAndStoreIdOrderByImageOrderAsc(batchId, storeId)).hasSize(10);

        var replay = multipart("/v2/menu-images").session(session).header("Idempotency-Key", "upload-10");
        for (int index = 0; index < 10; index++) {
            replay.file(image(index));
        }
        mvc.perform(replay).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value(batchId));
        verify(ai, times(1)).recognize(any());

        String batchBody = mvc.perform(get("/v2/menu-image-batches/{batchId}", batchId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.detectedItems.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        var candidates = json.readTree(batchBody).path("data").path("detectedItems");
        mvc.perform(post("/v2/menu-image-batches/{batchId}/confirmations", batchId)
                        .session(session).header("Idempotency-Key", "confirm-10")
                        .contentType("application/json")
                        .content("""
                                {"expectedMenuRevision":0,"items":[
                                {"itemId":%d,"name":"아메리카노","price":4000,"category":"COFFEE","order":1},
                                {"itemId":%d,"name":"카페라떼","price":4500,"category":"COFFEE","order":2}
                                ]}
                                """.formatted(candidates.get(0).path("itemId").asLong(),
                                candidates.get(1).path("itemId").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuRevision").value(1));
        mvc.perform(get("/v2/menu-items").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuRevision").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.draftBatch").isEmpty());
        assertThat(menus.findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(storeId)).hasSize(2);
        assertThat(batches.findById(batchId).orElseThrow().getSavedAt()).isNotNull();
    }

    @Test
    void AI_실패는_실패_배치로_남고_재업로드로_회복한다() throws Exception {
        when(ai.recognize(any()))
                .thenThrow(new MenuRecognitionAiException("LOW_IMAGE_QUALITY", "흐린 이미지"))
                .thenAnswer(invocation -> {
                    MenuRecognitionRequest request = invocation.getArgument(0);
                    return new MenuRecognitionResponse("ok", new MenuRecognitionResponse.Data(List.of(
                            detected(request.images().getFirst().imageId(), "아메리카노", 4000))));
                });

        String failedBody = mvc.perform(multipart("/v2/menu-images").file(image(0)).session(session)
                        .header("Idempotency-Key", "first-upload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failReason").value("LOW_IMAGE_QUALITY"))
                .andReturn().getResponse().getContentAsString();
        long failedBatchId = json.readTree(failedBody).path("data").path("batchId").asLong();
        mvc.perform(get("/v2/menu-image-batches/{batchId}", failedBatchId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(menus.findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(storeId)).isEmpty();

        String recoveredBody = mvc.perform(multipart("/v2/menu-images").file(image(0)).session(session)
                        .header("Idempotency-Key", "second-upload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        long recoveredBatchId = json.readTree(recoveredBody).path("data").path("batchId").asLong();
        assertThat(recoveredBatchId).isNotEqualTo(failedBatchId);
        assertThat(batches.findById(failedBatchId).orElseThrow().getSupersededAt()).isNotNull();
        verify(ai, times(2)).recognize(any());
    }

    @Test
    void 한_이미지가_실패해도_성공_후보를_확정할_수_있다() throws Exception {
        when(ai.recognize(any())).thenAnswer(invocation -> {
            MenuRecognitionRequest request = invocation.getArgument(0);
            return new MenuRecognitionResponse("ok", new MenuRecognitionResponse.Data(List.of(
                    detected(request.images().get(0).imageId(), "아메리카노", 4000)),
                    List.of(new MenuRecognitionResponse.FailedImage(
                            request.images().get(1).imageId(), "LOW_IMAGE_QUALITY"))));
        });

        String uploadBody = mvc.perform(multipart("/v2/menu-images")
                        .file(image(0)).file(image(1)).session(session)
                        .header("Idempotency-Key", "partial-upload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        long batchId = json.readTree(uploadBody).path("data").path("batchId").asLong();
        var storedImages = images.findAllByBatchIdAndStoreIdOrderByImageOrderAsc(batchId, storeId);
        assertThat(storedImages).extracting(image -> image.getOcrStatus().name())
                .containsExactly("COMPLETED", "FAILED");

        String batchBody = mvc.perform(get("/v2/menu-image-batches/{batchId}", batchId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.detectedItems.length()").value(1))
                .andExpect(jsonPath("$.data.failedImages[0].order").value(2))
                .andExpect(jsonPath("$.data.failedImages[0].failReason").value("LOW_IMAGE_QUALITY"))
                .andReturn().getResponse().getContentAsString();
        long candidateId = json.readTree(batchBody).path("data").path("detectedItems")
                .get(0).path("itemId").asLong();
        mvc.perform(post("/v2/menu-image-batches/{batchId}/confirmations", batchId)
                        .session(session).header("Idempotency-Key", "partial-confirm")
                        .contentType("application/json")
                        .content("""
                                {"expectedMenuRevision":0,"items":[
                                {"itemId":%d,"name":"아메리카노","price":4000,
                                "category":"COFFEE","order":1}]}
                                """.formatted(candidateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuRevision").value(1));
        assertThat(menus.findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(storeId))
                .hasSize(1);
    }

    private MockMultipartFile image(int index) {
        return new MockMultipartFile("images", "menu-" + index + ".jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) index});
    }

    private MenuRecognitionResponse.DetectedItem detected(long imageId, String name, long price) {
        return new MenuRecognitionResponse.DetectedItem(imageId, name, price, "COFFEE",
                new BigDecimal("0.98"), false);
    }
}
