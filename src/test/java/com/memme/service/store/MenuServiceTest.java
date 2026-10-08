package com.memme.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memme.dto.store.MenuConfirmationRequest;
import com.memme.dto.store.MenuPatchRequest;
import com.memme.entity.auth.User;
import com.memme.entity.store.Menu;
import com.memme.entity.store.MenuCategory;
import com.memme.entity.store.MenuImage;
import com.memme.entity.store.MenuImageItem;
import com.memme.entity.store.MenuUploadBatch;
import com.memme.entity.store.Store;
import com.memme.exception.store.MenuRequestException;
import com.memme.repository.auth.UserRepository;
import com.memme.repository.store.MenuImageItemRepository;
import com.memme.repository.store.MenuImageRepository;
import com.memme.repository.store.MenuRepository;
import com.memme.repository.store.MenuUploadBatchRepository;
import com.memme.repository.store.StoreRepository;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MenuServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 10, 0);

    @Autowired UserRepository users;
    @Autowired StoreRepository stores;
    @Autowired MenuRepository menus;
    @Autowired MenuUploadBatchRepository batches;
    @Autowired MenuImageRepository images;
    @Autowired MenuImageItemRepository candidates;
    @Autowired MenuQueryService query;
    @Autowired MenuWriteService writes;
    @Autowired MenuUploadService uploads;
    @Autowired MenuAnalysisLifecycleService lifecycle;

    @Test
    void 저장_메뉴와_임시_배치를_분리해_조회한다() {
        Store store = store();
        menus.save(Menu.create(store.getId(), "아메리카노", MenuCategory.COFFEE, 4000, 1, NOW));
        MenuUploadBatch batch = batches.save(MenuUploadBatch.pending(store.getId(), 0,
                "a".repeat(64), "b".repeat(64), NOW));

        var result = query.getMenuList(ownerId(store), store.getId());

        assertThat(result.items()).hasSize(1);
        assertThat(result.draftBatch().batchId()).isEqualTo(batch.getId());
        assertThat(result.items().getFirst().name()).isEqualTo("아메리카노");
    }

    @Test
    void 저장_메뉴_수정은_기존_행을_보존하고_개정을_올리며_재요청을_중복_적용하지_않는다() {
        Store store = store();
        Menu old = menus.save(Menu.create(store.getId(), "아메리카노", MenuCategory.COFFEE, 4000, 1, NOW));
        var request = new MenuPatchRequest(0L, List.of(new MenuPatchRequest.Item(
                old.getId(), "아메리카노", 4500L, "COFFEE", 1, null)));

        var first = writes.patch(ownerId(store), store.getId(), "patch-key", request);
        var repeated = writes.patch(ownerId(store), store.getId(), "patch-key", request);

        assertThat(first).isEqualTo(repeated);
        assertThat(first.menuRevision()).isEqualTo(1);
        assertThat(first.items().getFirst().id()).isNotEqualTo(old.getId());
        assertThat(menus.findById(old.getId()).orElseThrow().getRemovedAt()).isNotNull();
        assertThat(menus.findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(store.getId()))
                .extracting(Menu::getPrice).containsExactly(4500L);
    }

    @Test
    void 같은_이름_가격_카테고리_조합은_저장하지_않는다() {
        Store store = store();
        Menu first = menus.save(Menu.create(store.getId(), "아메리카노", MenuCategory.COFFEE, 4000, 1, NOW));
        Menu second = menus.save(Menu.create(store.getId(), "라떼", MenuCategory.COFFEE, 4500, 2, NOW));
        var request = new MenuPatchRequest(0L, List.of(
                new MenuPatchRequest.Item(first.getId(), " 아메리카노 ", 4000L, "COFFEE", 1, null),
                new MenuPatchRequest.Item(second.getId(), "아메리카노", 4000L, "COFFEE", 2, null)));

        assertThatThrownBy(() -> writes.patch(ownerId(store), store.getId(), "duplicate", request))
                .isInstanceOfSatisfying(MenuRequestException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 후보_전체를_확정하면_기존_메뉴를_숨기고_중복_원본을_같은_메뉴에_연결한다() {
        Store store = store();
        Menu old = menus.save(Menu.create(store.getId(), "기존 메뉴", MenuCategory.OTHER, 1000, 1, NOW));
        MenuUploadBatch batch = completedBatch(store);
        MenuImage firstImage = completedImage(batch, store, 1);
        MenuImage secondImage = completedImage(batch, store, 2);
        MenuImageItem first = candidates.save(MenuImageItem.pending(firstImage.getId(),
                "아메리카노", "COFFEE", 4000L, 1, null, false, NOW));
        MenuImageItem duplicate = candidates.save(MenuImageItem.pending(secondImage.getId(),
                "아메리카노", "COFFEE", 4000L, 2, null, false, NOW));
        var request = new MenuConfirmationRequest(0L, List.of(new MenuConfirmationRequest.Item(
                first.getId(), "아메리카노", 4000L, "COFFEE", 1, null)));

        var response = writes.confirm(ownerId(store), store.getId(), batch.getId(), "confirm", request);

        assertThat(response.menuRevision()).isEqualTo(1);
        assertThat(query.getBatch(ownerId(store), store.getId(), batch.getId()).data().detectedItems())
                .hasSize(1);
        assertThat(candidates.findById(first.getId()).orElseThrow().getMenuId())
                .isEqualTo(response.items().getFirst().menuId());
        assertThat(candidates.findById(duplicate.getId()).orElseThrow().getMenuId())
                .isEqualTo(response.items().getFirst().menuId());
        assertThat(menus.findById(old.getId()).orElseThrow().getRemovedAt()).isNotNull();
        assertThat(query.getMenuList(ownerId(store), store.getId()).draftBatch()).isNull();
    }

    @Test
    void 이미지를_접수하고_같은_멱등_요청은_기존_배치를_반환한다() {
        Store store = store();
        var image = new MockMultipartFile("images", "menu.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9});

        var first = uploads.upload(ownerId(store), store.getId(), "upload-key", List.of(image));
        var repeated = uploads.upload(ownerId(store), store.getId(), "upload-key", List.of(image));

        assertThat(repeated).isEqualTo(first);
        assertThat(first.imageCount()).isEqualTo(1);
        assertThat(images.findAllByBatchIdAndStoreIdOrderByImageOrderAsc(first.batchId(), store.getId()))
                .hasSize(1);
    }

    @Test
    void 이미지_확장자와_MIME과_시그니처가_맞지_않으면_접수하지_않는다() {
        Store store = store();
        var fake = new MockMultipartFile("images", "menu.jpg", "image/jpeg",
                new byte[] {1, 2, 3, 4});

        assertThatThrownBy(() -> uploads.upload(ownerId(store), store.getId(), "bad-image", List.of(fake)))
                .isInstanceOfSatisfying(MenuRequestException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(error.getData()).isEqualTo(java.util.Map.of(
                            "code", "UNSUPPORTED_FILE", "fileIndex", 0));
                });
    }

    @Test
    void 이미지가_열장을_초과하면_접수하지_않는다() {
        Store store = store();
        var image = new MockMultipartFile("images", "menu.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9});

        assertThatThrownBy(() -> uploads.upload(ownerId(store), store.getId(), "too-many",
                java.util.Collections.nCopies(11, image)))
                .isInstanceOfSatisfying(MenuRequestException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getData()).isEqualTo(java.util.Map.of("code", "TOO_MANY_IMAGES"));
                });
    }

    @Test
    void AI_처리_완료는_원본_이미지와_후보를_저장하고_배치_상태를_갱신한다() {
        Store store = store();
        MenuUploadBatch batch = batches.save(MenuUploadBatch.pending(store.getId(), 0,
                "c".repeat(64), "d".repeat(64), NOW));
        MenuImage image = images.save(MenuImage.pending(batch.getId(), store.getId(), 1,
                "menu/" + UUID.randomUUID() + ".png", 100, "image/png", NOW));

        lifecycle.begin(store.getId(), batch.getId());
        lifecycle.complete(store.getId(), batch.getId(), List.of(new MenuRecognitionAiClient.DetectedItem(
                image.getId(), "아메리카노", 4000L, "COFFEE", new BigDecimal("0.9800"), false)));

        var result = query.getBatch(ownerId(store), store.getId(), batch.getId());
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.data().detectedItems()).hasSize(1);
        assertThat(result.data().detectedItems().getFirst().name()).isEqualTo("아메리카노");
        assertThat(images.findById(image.getId()).orElseThrow().getOcrResult()).contains("아메리카노");
    }

    @Test
    void 가격_확인이_필요한_후보는_확인이나_수정_전까지_저장하지_않는다() {
        Store store = store();
        MenuUploadBatch batch = completedBatch(store);
        MenuImage image = completedImage(batch, store, 1);
        MenuImageItem candidate = candidates.save(MenuImageItem.pending(image.getId(),
                "쿠키", "OTHER", 9000L, 1, null, true, NOW));
        var request = new MenuConfirmationRequest(0L, List.of(new MenuConfirmationRequest.Item(
                candidate.getId(), "쿠키", 9000L, "OTHER", 1, false)));

        assertThatThrownBy(() -> writes.confirm(ownerId(store), store.getId(), batch.getId(),
                "unchecked-price", request))
                .isInstanceOfSatisfying(MenuRequestException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
    }

    private Store store() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(User.create("menu-" + suffix + "@example.com", "encoded-password",
                "010" + suffix.replaceAll("[^0-9]", "1").substring(0, 8), NOW));
        return stores.save(Store.create(user, null, null, "맴매카페", "06236",
                "서울특별시 강남구 테헤란로 123", null, NOW));
    }

    private long ownerId(Store store) {
        return stores.findOwnerUserIdByStoreId(store.getId()).orElseThrow();
    }

    private MenuUploadBatch completedBatch(Store store) {
        MenuUploadBatch batch = batches.save(MenuUploadBatch.pending(store.getId(), 0,
                UUID.randomUUID().toString().replace("-", "") + "a".repeat(32), "b".repeat(64), NOW));
        batch.startProcessing();
        batch.complete(NOW.plusMinutes(1));
        return batch;
    }

    private MenuImage completedImage(MenuUploadBatch batch, Store store, int order) {
        MenuImage image = images.save(MenuImage.pending(batch.getId(), store.getId(), order,
                "menu/" + UUID.randomUUID() + ".png", 100, "image/png", NOW));
        image.startProcessing();
        image.complete("{}");
        return image;
    }
}
