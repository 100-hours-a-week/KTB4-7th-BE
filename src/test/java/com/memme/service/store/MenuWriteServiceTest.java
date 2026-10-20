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
import com.memme.entity.store.MenuImageItemStatus;
import com.memme.entity.store.MenuProcessingStatus;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

@SpringBootTest
class MenuWriteServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 10, 0);

    @Autowired private UserRepository users;
    @Autowired private StoreRepository stores;
    @Autowired private MenuRepository menus;
    @Autowired private MenuUploadBatchRepository batches;
    @Autowired private MenuImageRepository images;
    @Autowired private MenuImageItemRepository candidates;
    @Autowired private MenuWriteService writes;

    @Test
    void 기존_메뉴_한_건을_수정하면_이력을_남기고_다른_메뉴는_유지한다() {
        Store store = store();
        Menu coffee = menus.save(Menu.create(store.getId(), "아메리카노", MenuCategory.COFFEE,
                4000, 1, NOW));
        Menu cake = menus.save(Menu.create(store.getId(), "케이크", MenuCategory.CAKE,
                6000, 2, NOW));
        var request = new MenuPatchRequest(0L, List.of(
                new MenuPatchRequest.Item(coffee.getId(), " 아메리카노 ", 4500L, "COFFEE", 1, null),
                new MenuPatchRequest.Item(cake.getId(), "케이크", 6000L, "CAKE", 2, null)));

        var first = writes.patch(ownerId(store), store.getId(), "patch-" + UUID.randomUUID(), request);

        assertThat(first.menuRevision()).isEqualTo(1);
        assertThat(first.items()).hasSize(2);
        assertThat(first.items().getFirst().id()).isNotEqualTo(coffee.getId());
        assertThat(first.items().get(1).id()).isEqualTo(cake.getId());
        assertThat(menus.findById(coffee.getId()).orElseThrow().getRemovedAt()).isNotNull();
        assertThat(menus.findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(store.getId()))
                .extracting(Menu::getId).containsExactly(first.items().getFirst().id(), cake.getId());
    }

    @Test
    void 메뉴판_카테고리는_TEA를_허용하고_NON_COFFEE를_거절한다() {
        Store store = store();
        Menu menu = menus.save(Menu.create(store.getId(), "녹차", MenuCategory.OTHER, 4500, 1, NOW));
        var teaRequest = new MenuPatchRequest(0L, List.of(new MenuPatchRequest.Item(
                menu.getId(), "녹차", 4500L, "TEA", 1, null)));

        var response = writes.patch(ownerId(store), store.getId(),
                "tea-" + UUID.randomUUID(), teaRequest);

        assertThat(response.items().getFirst().category()).isEqualTo("TEA");
        Long teaMenuId = response.items().getFirst().id();
        assertThat(menus.findById(teaMenuId).orElseThrow().getCategory()).isEqualTo(MenuCategory.TEA);

        var oldCategoryRequest = new MenuPatchRequest(1L, List.of(new MenuPatchRequest.Item(
                teaMenuId, "녹차", 4500L, "NON_COFFEE", 1, null)));
        assertThatThrownBy(() -> writes.patch(ownerId(store), store.getId(),
                "old-category-" + UUID.randomUUID(), oldCategoryRequest))
                .isInstanceOfSatisfying(MenuRequestException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.getData().toString()).contains("INVALID_CATEGORY");
                });
    }

    @Test
    void 동일_멱등_키의_재요청은_기존_응답을_반환하고_다른_본문은_거절한다() {
        Store store = store();
        Menu coffee = menus.save(Menu.create(store.getId(), "커피", MenuCategory.COFFEE, 4000, 1, NOW));
        String key = "same-" + UUID.randomUUID();
        var request = new MenuPatchRequest(0L, List.of(
                new MenuPatchRequest.Item(coffee.getId(), "커피", 4500L, "COFFEE", 1, null)));

        var first = writes.patch(ownerId(store), store.getId(), key, request);
        var repeated = writes.patch(ownerId(store), store.getId(), key, request);

        assertThat(repeated).isEqualTo(first);
        assertThat(stores.findById(store.getId()).orElseThrow().getMenuRevision()).isEqualTo(1);
        assertThatThrownBy(() -> writes.patch(ownerId(store), store.getId(), key,
                new MenuPatchRequest(0L, List.of(new MenuPatchRequest.Item(
                        coffee.getId(), "커피", 5000L, "COFFEE", 1, null)))))
                .isInstanceOfSatisfying(MenuRequestException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 전체_후보_확정은_기존_메뉴를_논리_제거하고_중복_원본을_연결한다() {
        Store store = store();
        Menu old = menus.save(Menu.create(store.getId(), "기존 메뉴", MenuCategory.OTHER, 1000, 1, NOW));
        MenuUploadBatch batch = completedBatch(store);
        MenuImage firstImage = image(batch, store, 1);
        MenuImage secondImage = image(batch, store, 2);
        MenuImageItem first = candidates.save(MenuImageItem.pending(firstImage.getId(),
                "아메리카노", "COFFEE", 4000L, 0, null, false, NOW));
        MenuImageItem duplicate = candidates.save(MenuImageItem.pending(secondImage.getId(),
                " 아메리카노 ", "COFFEE", 4000L, 0, null, false, NOW));
        var request = new MenuConfirmationRequest(0L, List.of(
                new MenuConfirmationRequest.Item(first.getId(), "아메리카노", 4000L,
                        "COFFEE", 1, null)));

        var response = writes.confirm(ownerId(store), store.getId(), batch.getId(),
                "confirm-" + UUID.randomUUID(), request);

        assertThat(response.menuRevision()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        long savedMenuId = response.items().getFirst().menuId();
        assertThat(candidates.findById(first.getId()).orElseThrow().getMenuId()).isEqualTo(savedMenuId);
        assertThat(candidates.findById(duplicate.getId()).orElseThrow().getMenuId()).isEqualTo(savedMenuId);
        assertThat(candidates.findById(duplicate.getId()).orElseThrow().getStatus())
                .isEqualTo(MenuImageItemStatus.CONFIRMED);
        assertThat(batches.findById(batch.getId()).orElseThrow().getSavedAt()).isNotNull();
        assertThat(menus.findById(old.getId()).orElseThrow().getRemovedAt()).isNotNull();
        assertThat(images.findById(firstImage.getId()).orElseThrow().getOcrStatus())
                .isEqualTo(MenuProcessingStatus.COMPLETED);
    }

    @Test
    void 카테고리가_없는_후보는_선택하기_전까지_확정할_수_없다() {
        Store store = store();
        MenuUploadBatch batch = completedBatch(store);
        MenuImage image = image(batch, store, 1);
        MenuImageItem candidate = candidates.save(MenuImageItem.pending(image.getId(),
                "녹차", null, 4500L, 0, null, false, NOW));
        var missingCategory = new MenuConfirmationRequest(0L, List.of(
                new MenuConfirmationRequest.Item(candidate.getId(), "녹차", 4500L,
                        null, 1, null)));

        assertThatThrownBy(() -> writes.confirm(ownerId(store), store.getId(), batch.getId(),
                "missing-category-" + UUID.randomUUID(), missingCategory))
                .isInstanceOfSatisfying(MenuRequestException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.getData().toString()).contains("INVALID_CATEGORY");
                });
        assertThat(menus.findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(store.getId()))
                .isEmpty();
        assertThat(batches.findById(batch.getId()).orElseThrow().getSavedAt()).isNull();
        assertThat(stores.findById(store.getId()).orElseThrow().getMenuRevision()).isZero();

        var selectedCategory = new MenuConfirmationRequest(0L, List.of(
                new MenuConfirmationRequest.Item(candidate.getId(), "녹차", 4500L,
                        "TEA", 1, null)));
        var saved = writes.confirm(ownerId(store), store.getId(), batch.getId(),
                "selected-category-" + UUID.randomUUID(), selectedCategory);

        assertThat(saved.items().getFirst().category()).isEqualTo("TEA");
        assertThat(batches.findById(batch.getId()).orElseThrow().getSavedAt()).isNotNull();
    }

    @Test
    void 가격_확인_누락은_전체를_거절하고_기존_메뉴를_유지한다() {
        Store store = store();
        Menu old = menus.save(Menu.create(store.getId(), "기존 메뉴", MenuCategory.OTHER, 1000, 1, NOW));
        MenuUploadBatch batch = completedBatch(store);
        MenuImage image = image(batch, store, 1);
        MenuImageItem candidate = candidates.save(MenuImageItem.pending(image.getId(),
                "쿠키", "OTHER", 9000L, 0, null, true, NOW));
        var request = new MenuConfirmationRequest(0L, List.of(
                new MenuConfirmationRequest.Item(candidate.getId(), "쿠키", 9000L,
                        "OTHER", 1, false)));

        assertThatThrownBy(() -> writes.confirm(ownerId(store), store.getId(), batch.getId(),
                "unconfirmed-" + UUID.randomUUID(), request))
                .isInstanceOfSatisfying(MenuRequestException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));

        assertThat(menus.findById(old.getId()).orElseThrow().getRemovedAt()).isNull();
        assertThat(stores.findById(store.getId()).orElseThrow().getMenuRevision()).isZero();
        assertThat(batches.findById(batch.getId()).orElseThrow().getSavedAt()).isNull();
    }

    @Test
    void 메뉴_중복과_개정_충돌은_기존_목록을_바꾸지_않는다() {
        Store store = store();
        Menu first = menus.save(Menu.create(store.getId(), "커피", MenuCategory.COFFEE, 4000, 1, NOW));
        Menu second = menus.save(Menu.create(store.getId(), "라떼", MenuCategory.COFFEE, 4500, 2, NOW));
        var duplicate = new MenuPatchRequest(0L, List.of(
                new MenuPatchRequest.Item(first.getId(), "커피", 4000L, "COFFEE", 1, null),
                new MenuPatchRequest.Item(second.getId(), " 커피 ", 4000L, "COFFEE", 2, null)));

        assertThatThrownBy(() -> writes.patch(ownerId(store), store.getId(),
                "duplicate-" + UUID.randomUUID(), duplicate))
                .isInstanceOfSatisfying(MenuRequestException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> writes.patch(ownerId(store), store.getId(),
                "stale-" + UUID.randomUUID(), new MenuPatchRequest(2L, duplicate.items())))
                .isInstanceOfSatisfying(MenuRequestException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(menus.findById(second.getId()).orElseThrow().getRemovedAt()).isNull();
        assertThat(stores.findById(store.getId()).orElseThrow().getMenuRevision()).isZero();
    }

    @Test
    void 수정_요청의_필드_오류와_변경_없음을_구분한다() {
        Store store = store();
        Menu menu = menus.save(Menu.create(store.getId(), "커피", MenuCategory.COFFEE, 4000, 1, NOW));
        var invalid = new MenuPatchRequest(0L, List.of(new MenuPatchRequest.Item(
                menu.getId(), " ", -1L, "INVALID", 0, null)));

        assertThatThrownBy(() -> writes.patch(ownerId(store), store.getId(),
                "invalid-" + UUID.randomUUID(), invalid))
                .isInstanceOfSatisfying(MenuRequestException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.getData().toString()).contains("INVALID_NAME", "OUT_OF_RANGE",
                            "INVALID_CATEGORY", "INVALID_ORDER");
                });
        assertThatThrownBy(() -> writes.patch(ownerId(store), store.getId(),
                "unchanged-" + UUID.randomUUID(), new MenuPatchRequest(0L, List.of(
                        new MenuPatchRequest.Item(menu.getId(), "커피", 4000L,
                                "COFFEE", 1, null)))))
                .isInstanceOfSatisfying(MenuRequestException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(menus.findById(menu.getId()).orElseThrow().getRemovedAt()).isNull();
    }

    @Test
    void 이전_배치는_완료되었어도_확정하지_않는다() {
        Store store = store();
        MenuUploadBatch older = completedBatch(store);
        MenuUploadBatch newer = batches.save(MenuUploadBatch.pending(store.getId(), 0,
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                "new-request", NOW.plusMinutes(1)));
        MenuImage image = image(older, store, 1);
        MenuImageItem candidate = candidates.save(MenuImageItem.pending(image.getId(),
                "커피", "COFFEE", 4000L, 0, null, false, NOW));
        var request = new MenuConfirmationRequest(0L, List.of(
                new MenuConfirmationRequest.Item(candidate.getId(), "커피", 4000L,
                        "COFFEE", 1, null)));

        assertThatThrownBy(() -> writes.confirm(ownerId(store), store.getId(), older.getId(),
                "old-" + UUID.randomUUID(), request))
                .isInstanceOfSatisfying(MenuRequestException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(batches.findById(older.getId()).orElseThrow().getSavedAt()).isNull();
        assertThat(batches.findById(newer.getId())).isPresent();
    }

    private Store store() {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User owner = users.save(User.create("write-" + id + "@example.com", "encoded-password",
                "010" + id.replaceAll("[^0-9]", "1").substring(0, 8), NOW));
        return stores.save(Store.create(owner, null, null, "맴매카페", "06236",
                "서울특별시 강남구 테헤란로 123", null, NOW));
    }

    private long ownerId(Store store) {
        return stores.findOwnerUserIdByStoreId(store.getId()).orElseThrow();
    }

    private MenuUploadBatch completedBatch(Store store) {
        String id = UUID.randomUUID().toString().replace("-", "");
        MenuUploadBatch batch = batches.save(MenuUploadBatch.pending(store.getId(), 0,
                id.repeat(2), id.repeat(2), NOW));
        batch.startProcessing();
        batch.complete(NOW);
        return batches.saveAndFlush(batch);
    }

    private MenuImage image(MenuUploadBatch batch, Store store, int order) {
        MenuImage image = MenuImage.pending(batch.getId(), store.getId(), order,
                "menu-images/" + store.getId() + "/" + UUID.randomUUID() + ".jpg",
                4, "image/jpeg", NOW);
        image.startProcessing();
        image.complete("{}");
        return images.save(image);
    }
}
