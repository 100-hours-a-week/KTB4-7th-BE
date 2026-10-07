package com.memme.repository.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.memme.entity.auth.User;
import com.memme.entity.store.Menu;
import com.memme.entity.store.MenuCategory;
import com.memme.entity.store.MenuImage;
import com.memme.entity.store.MenuImageItem;
import com.memme.entity.store.MenuProcessingStatus;
import com.memme.entity.store.MenuUploadBatch;
import com.memme.entity.store.MenuWriteOperation;
import com.memme.entity.store.MenuWriteRequest;
import com.memme.entity.store.Store;
import com.memme.repository.auth.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MenuPersistenceRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 13, 0);

    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private MenuRepository menuRepository;
    @Autowired private MenuUploadBatchRepository batchRepository;
    @Autowired private MenuImageRepository imageRepository;
    @Autowired private MenuImageItemRepository itemRepository;
    @Autowired private MenuWriteRequestRepository writeRequestRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void 현재_메뉴만_매장별_순서대로_조회하고_개정_번호를_보존한다() {
        Store first = createStore("menu-one@example.com", "01011112222");
        Store second = createStore("menu-two@example.com", "01033334444");
        Menu later = menuRepository.save(Menu.create(first.getId(), " 카페   라떼 ", MenuCategory.COFFEE,
                4500, 2, NOW));
        Menu earlier = menuRepository.save(Menu.create(first.getId(), "아메리카노", MenuCategory.COFFEE,
                4000, 1, NOW));
        menuRepository.save(Menu.create(second.getId(), "다른 매장 메뉴", MenuCategory.OTHER,
                1000, 1, NOW));
        first.advanceMenuRevision(0, NOW.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        List<Menu> current = menuRepository.findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(first.getId());
        assertThat(current).extracting(Menu::getId).containsExactly(earlier.getId(), later.getId());
        assertThat(current.get(1).getNormalizedName()).isEqualTo("카페 라떼");
        assertThat(storeRepository.findForMenuUpdate(first.getId()).orElseThrow().getMenuRevision()).isEqualTo(1);

        current.get(0).remove(NOW.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();
        assertThat(menuRepository.findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(first.getId()))
                .extracting(Menu::getId).containsExactly(later.getId());
        assertThat(menuRepository.findByIdAndStoreIdAndRemovedAtIsNull(earlier.getId(), first.getId())).isEmpty();
    }

    @Test
    void 배치_이미지_후보와_저장_요청을_매장_기준으로_조회한다() {
        Store store = createStore("menu-three@example.com", "01055556666");
        MenuUploadBatch batch = batchRepository.save(MenuUploadBatch.pending(store.getId(), 0,
                "a".repeat(64), "b".repeat(64), NOW));
        MenuImage image = imageRepository.save(MenuImage.pending(batch.getId(), store.getId(), 1,
                "menu/test.jpg", 1000, "image/jpeg", NOW));
        MenuImageItem item = itemRepository.save(MenuImageItem.pending(image.getId(),
                "아메리카노", "COFFEE", 4000L, 1, new BigDecimal("0.9800"), false, NOW));
        writeRequestRepository.save(MenuWriteRequest.create(store.getId(), MenuWriteOperation.MENU_CONFIRM,
                "c".repeat(64), "d".repeat(64), "{\"menuRevision\":1}", NOW));
        entityManager.flush();
        entityManager.clear();

        assertThat(batchRepository.findFirstByStoreIdOrderByUploadedAtDescIdDesc(store.getId()))
                .get().extracting(MenuUploadBatch::getStatus).isEqualTo(MenuProcessingStatus.PENDING);
        assertThat(batchRepository.findByIdAndStoreId(batch.getId(), store.getId() + 1)).isEmpty();
        assertThat(imageRepository.findAllByBatchIdAndStoreIdOrderByImageOrderAsc(batch.getId(), store.getId()))
                .extracting(MenuImage::getId).containsExactly(image.getId());
        assertThat(itemRepository.findAllByMenuImageIdInOrderBySortOrderAscIdAsc(List.of(image.getId())))
                .extracting(MenuImageItem::getId).containsExactly(item.getId());
        assertThat(writeRequestRepository.findByStoreIdAndOperationAndKeyHash(
                store.getId(), MenuWriteOperation.MENU_CONFIRM, "c".repeat(64)))
                .get().extracting(MenuWriteRequest::getResponseJson).isEqualTo("{\"menuRevision\":1}");
    }

    private Store createStore(String email, String phone) {
        User owner = userRepository.save(User.create(email, "encoded-password", phone, NOW));
        return storeRepository.save(Store.create(owner, null, null, "맴매카페", "06236",
                "서울특별시 강남구 테헤란로 123", null, NOW));
    }
}
