package com.memme.service.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.entity.store.Menu;
import com.memme.entity.store.MenuCategory;
import com.memme.entity.store.MenuImage;
import com.memme.entity.store.MenuImageItem;
import com.memme.entity.store.MenuProcessingStatus;
import com.memme.entity.store.MenuUploadBatch;
import com.memme.entity.store.Store;
import com.memme.exception.store.MenuRequestException;
import com.memme.repository.store.MenuImageItemRepository;
import com.memme.repository.store.MenuImageRepository;
import com.memme.repository.store.MenuRepository;
import com.memme.repository.store.MenuUploadBatchRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.repository.store.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class MenuQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 10, 0);

    private final StoreRepository stores = mock(StoreRepository.class);
    private final StoreOwnershipRepository ownership = mock(StoreOwnershipRepository.class);
    private final MenuRepository menus = mock(MenuRepository.class);
    private final MenuUploadBatchRepository batches = mock(MenuUploadBatchRepository.class);
    private final MenuImageRepository images = mock(MenuImageRepository.class);
    private final MenuImageItemRepository candidates = mock(MenuImageItemRepository.class);
    private final MenuQueryService query = new MenuQueryService(
            stores, ownership, menus, batches, images, candidates);

    @BeforeEach
    void owner() {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(7L);
        when(store.getMenuRevision()).thenReturn(4L);
        when(stores.findByOwnerId(3L)).thenReturn(Optional.of(store));
        when(ownership.existsActiveStoreOwnedBy(7L, 3L)).thenReturn(true);
    }

    @Test
    void 현재_메뉴와_실패한_최신_임시_배치를_각각_반환한다() {
        Menu menu = Menu.create(7L, "아메리카노", MenuCategory.COFFEE, 4000, 1, NOW);
        ReflectionTestUtils.setField(menu, "id", 27L);
        MenuUploadBatch batch = batch(15L);
        batch.fail("LOW_IMAGE_QUALITY", NOW);
        when(menus.findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(7L))
                .thenReturn(List.of(menu));
        when(batches.findFirstByStoreIdOrderByUploadedAtDescIdDesc(7L))
                .thenReturn(Optional.of(batch));

        var result = query.getMenuList(3L, 7L);

        assertThat(result.menuRevision()).isEqualTo(4);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().updatedAt().getOffset().getTotalSeconds())
                .isEqualTo(9 * 3600);
        assertThat(result.draftBatch().status()).isEqualTo("FAILED");
        assertThat(result.draftBatch().failReason()).isEqualTo("LOW_IMAGE_QUALITY");
    }

    @Test
    void 저장되거나_교체된_배치는_임시_배치에서_제외한다() {
        MenuUploadBatch saved = batch(15L);
        saved.startProcessing();
        saved.complete(NOW);
        saved.markSaved(NOW);
        when(batches.findFirstByStoreIdOrderByUploadedAtDescIdDesc(7L))
                .thenReturn(Optional.of(saved));

        assertThat(query.getMenuList(3L, 7L).draftBatch()).isNull();

        MenuUploadBatch superseded = batch(16L);
        superseded.markSuperseded(NOW);
        when(batches.findFirstByStoreIdOrderByUploadedAtDescIdDesc(7L))
                .thenReturn(Optional.of(superseded));
        assertThat(query.getMenuList(3L, 7L).draftBatch()).isNull();
    }

    @Test
    void 완료_배치의_중복_후보를_이미지_순서대로_합치고_가격_확인을_전파한다() {
        MenuUploadBatch batch = batch(15L);
        batch.startProcessing();
        batch.complete(NOW);
        MenuImage firstImage = image(52L, 1);
        MenuImage secondImage = image(53L, 2);
        MenuImageItem coffee = item(101L, 52L, "아메리카노", 4000L,
                "COFFEE", 0, false);
        MenuImageItem cookie = item(102L, 52L, "쿠키", null,
                "OTHER", 1, true);
        MenuImageItem duplicate = item(103L, 53L, "  아메리카노  ", 4000L,
                "COFFEE", 0, true);
        MenuImageItem distinctPrice = item(104L, 53L, "아메리카노", 4500L,
                "COFFEE", 1, false);
        when(batches.findByIdAndStoreId(15L, 7L)).thenReturn(Optional.of(batch));
        when(images.findAllByBatchIdAndStoreIdOrderByImageOrderAsc(15L, 7L))
                .thenReturn(List.of(firstImage, secondImage));
        when(candidates.findAllByMenuImageIdInOrderBySortOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(coffee, duplicate, cookie, distinctPrice));

        var result = query.getBatch(3L, 7L, 15L);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.data().detectedItems()).hasSize(3);
        assertThat(result.data().detectedItems()).extracting("itemId")
                .containsExactly(101L, 102L, 104L);
        assertThat(result.data().detectedItems()).extracting("order")
                .containsExactly(1, 2, 3);
        assertThat(result.data().detectedItems().getFirst().priceReviewRequired()).isTrue();
        assertThat(result.data().detectedItems().get(1).price()).isNull();
    }

    @Test
    void 진행과_실패_상태에서는_후보를_조회하지_않는다() {
        MenuUploadBatch batch = batch(15L);
        batch.startProcessing();
        when(batches.findByIdAndStoreId(15L, 7L)).thenReturn(Optional.of(batch));

        assertThat(query.getBatch(3L, 7L, 15L).data().detectedItems()).isEmpty();
        assertThat(query.getBatch(3L, 7L, 15L).status()).isEqualTo("PROCESSING");
        batch.fail("MENU_TEXT_NOT_FOUND", NOW);
        assertThat(query.getBatch(3L, 7L, 15L).data().failReason())
                .isEqualTo("MENU_TEXT_NOT_FOUND");
        verify(images).findAllByBatchIdAndStoreIdOrderByImageOrderAsc(15L, 7L);
        verify(candidates, never()).findAllByMenuImageIdInOrderBySortOrderAscIdAsc(anyCollection());
    }

    @Test
    void 다른_매장과_없는_배치는_조회할_수_없다() {
        assertThatThrownBy(() -> query.getMenuList(3L, 8L))
                .isInstanceOfSatisfying(MenuRequestException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> query.getBatch(3L, 7L, 99L))
                .isInstanceOfSatisfying(MenuRequestException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(candidates, never()).findAllByMenuImageIdInOrderBySortOrderAscIdAsc(anyCollection());
    }

    private MenuUploadBatch batch(long id) {
        MenuUploadBatch batch = MenuUploadBatch.pending(7L, 4L, "key", "request", NOW);
        ReflectionTestUtils.setField(batch, "id", id);
        return batch;
    }

    private MenuImage image(long id, int order) {
        MenuImage image = MenuImage.pending(15L, 7L, order,
                "menu-images/7/" + id + ".jpg", 100, "image/jpeg", NOW);
        ReflectionTestUtils.setField(image, "id", id);
        return image;
    }

    private MenuImageItem item(long id, long imageId, String name, Long price,
            String category, int sortOrder, boolean priceReviewRequired) {
        MenuImageItem item = MenuImageItem.pending(imageId, name, category, price,
                sortOrder, new BigDecimal("0.9800"), priceReviewRequired, NOW);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
