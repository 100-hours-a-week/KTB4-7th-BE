package com.memme.service.store;

import com.memme.dto.store.MenuBatchResponse;
import com.memme.dto.store.MenuListResponse;
import com.memme.entity.store.Menu;
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
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuQueryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final StoreRepository stores;
    private final StoreOwnershipRepository ownership;
    private final MenuRepository menus;
    private final MenuUploadBatchRepository batches;
    private final MenuImageRepository images;
    private final MenuImageItemRepository candidates;

    public MenuQueryService(StoreRepository stores, StoreOwnershipRepository ownership,
            MenuRepository menus, MenuUploadBatchRepository batches,
            MenuImageRepository images, MenuImageItemRepository candidates) {
        this.stores = stores;
        this.ownership = ownership;
        this.menus = menus;
        this.batches = batches;
        this.images = images;
        this.candidates = candidates;
    }

    @Transactional(readOnly = true)
    public MenuListResponse getMenuList(long userId, long storeId) {
        Store store = requireOwnedStore(userId, storeId);
        List<MenuListResponse.Item> saved = menus
                .findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(storeId)
                .stream()
                .map(menu -> new MenuListResponse.Item(menu.getId(), menu.getSortOrder(),
                        menu.getName(), menu.getPrice(), menu.getCategory().name(),
                        atSeoul(menu.getUpdatedAt())))
                .toList();
        MenuListResponse.DraftBatch draft = batches
                .findFirstByStoreIdOrderByUploadedAtDescIdDesc(storeId)
                .filter(batch -> batch.getSavedAt() == null && batch.getSupersededAt() == null)
                .map(batch -> new MenuListResponse.DraftBatch(batch.getId(),
                        batch.getStatus().name(), atSeoul(batch.getUploadedAt()),
                        batch.getFailReason(), atSeoul(batch.getSavedAt())))
                .orElse(null);
        return new MenuListResponse(store.getMenuRevision(), saved, draft);
    }

    @Transactional(readOnly = true)
    public BatchResult getBatch(long userId, long storeId, long batchId) {
        requireOwnedStore(userId, storeId);
        MenuUploadBatch batch = batches.findByIdAndStoreId(batchId, storeId)
                .orElseThrow(() -> new MenuRequestException(HttpStatus.NOT_FOUND,
                        "메뉴판 분석 내역을 찾을 수 없습니다."));
        List<MenuBatchResponse.DetectedItem> detected = List.of();
        if (batch.getStatus() == MenuProcessingStatus.COMPLETED) {
            List<MenuImage> batchImages = images
                    .findAllByBatchIdAndStoreIdOrderByImageOrderAsc(batchId, storeId);
            if (!batchImages.isEmpty()) {
                List<Long> imageIds = batchImages.stream().map(MenuImage::getId).toList();
                List<MenuImageItem> original = candidates
                        .findAllByMenuImageIdInOrderBySortOrderAscIdAsc(imageIds);
                detected = distinctCandidates(batchImages, original);
            }
        }
        return new BatchResult(batch.getStatus().name(), new MenuBatchResponse(
                batch.getId(), batch.getBaseMenuRevision(), atSeoul(batch.getUploadedAt()),
                atSeoul(batch.getSavedAt()), batch.getFailReason(), detected));
    }

    Store requireOwnedStore(long userId, long storeId) {
        Store store = stores.findByOwnerId(userId)
                .orElseThrow(() -> new MenuRequestException(HttpStatus.NOT_FOUND,
                        "매장 정보를 찾을 수 없습니다."));
        if (!store.getId().equals(storeId)
                || !ownership.existsActiveStoreOwnedBy(storeId, userId)) {
            throw new MenuRequestException(HttpStatus.FORBIDDEN,
                    "이 매장의 메뉴를 조회할 권한이 없습니다.");
        }
        return store;
    }

    static OffsetDateTime atSeoul(LocalDateTime time) {
        return time == null ? null : time.atZone(SEOUL).toOffsetDateTime();
    }

    static List<MenuBatchResponse.DetectedItem> distinctCandidates(
            List<MenuImage> images, List<MenuImageItem> candidates) {
        Map<Long, Integer> imageOrder = new HashMap<>();
        for (MenuImage image : images) {
            imageOrder.put(image.getId(), image.getImageOrder());
        }
        List<MenuImageItem> sorted = candidates.stream()
                .filter(item -> imageOrder.containsKey(item.getMenuImageId()))
                .sorted(Comparator.comparingInt((MenuImageItem item) -> imageOrder.get(item.getMenuImageId()))
                        .thenComparingInt(MenuImageItem::getSortOrder)
                        .thenComparing(MenuImageItem::getId))
                .toList();
        Map<CandidateKey, Integer> firstPosition = new HashMap<>();
        List<MenuBatchResponse.DetectedItem> display = new ArrayList<>();
        for (MenuImageItem item : sorted) {
            CandidateKey key = candidateKey(item);
            Integer existing = key == null ? null : firstPosition.get(key);
            if (existing != null) {
                MenuBatchResponse.DetectedItem first = display.get(existing);
                if (item.isPriceReviewRequired() && !first.priceReviewRequired()) {
                    display.set(existing, new MenuBatchResponse.DetectedItem(first.itemId(),
                            first.order(), first.name(), first.price(), first.category(), true));
                }
                continue;
            }
            int position = display.size();
            if (key != null) {
                firstPosition.put(key, position);
            }
            display.add(new MenuBatchResponse.DetectedItem(item.getId(), position + 1,
                    item.getDetectedName(), item.getDetectedPrice(),
                    item.getDetectedCategory(), item.isPriceReviewRequired()));
        }
        return List.copyOf(display);
    }

    static CandidateKey candidateKey(MenuImageItem item) {
        if (item.getDetectedPrice() == null || item.getDetectedCategory() == null) {
            return null;
        }
        String name = Normalizer.normalize(
                item.getDetectedName().trim().replaceAll("\\s+", " "), Normalizer.Form.NFC);
        return new CandidateKey(name, item.getDetectedPrice(), item.getDetectedCategory());
    }

    record CandidateKey(String name, long price, String category) {}

    public record BatchResult(String status, MenuBatchResponse data) {}
}
