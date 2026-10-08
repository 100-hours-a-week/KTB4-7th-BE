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

    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final MenuUploadBatchRepository batchRepository;
    private final MenuImageRepository imageRepository;
    private final MenuImageItemRepository itemRepository;

    public MenuQueryService(StoreRepository storeRepository, MenuRepository menuRepository,
            MenuUploadBatchRepository batchRepository, MenuImageRepository imageRepository,
            MenuImageItemRepository itemRepository) {
        this.storeRepository = storeRepository;
        this.menuRepository = menuRepository;
        this.batchRepository = batchRepository;
        this.imageRepository = imageRepository;
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public MenuListResponse getMenuList(long userId, long storeId) {
        Store store = requireOwnedStore(userId, storeId);
        List<MenuListResponse.Item> menus = menuRepository
                .findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(storeId)
                .stream().map(this::toMenuItem).toList();
        MenuListResponse.DraftBatch draft = batchRepository.findFirstByStoreIdOrderByUploadedAtDescIdDesc(storeId)
                .filter(batch -> batch.getSavedAt() == null && batch.getSupersededAt() == null)
                .map(this::toDraftBatch).orElse(null);
        return new MenuListResponse(store.getMenuRevision(), menus, draft);
    }

    @Transactional(readOnly = true)
    public BatchResult getBatch(long userId, long storeId, long batchId) {
        requireOwnedStore(userId, storeId);
        MenuUploadBatch batch = batchRepository.findByIdAndStoreId(batchId, storeId)
                .orElseThrow(() -> new MenuRequestException(HttpStatus.NOT_FOUND, "메뉴판 분석 내역을 찾을 수 없습니다."));
        if (batch.getStatus() != MenuProcessingStatus.COMPLETED) {
            return new BatchResult(batch.getStatus().name(), new MenuBatchResponse(batchId, batch.getBaseMenuRevision(),
                    atSeoul(batch.getUploadedAt()), atSeoul(batch.getSavedAt()),
                    batch.getFailReason(), List.of()));
        }
        List<Long> imageIds = imageRepository.findAllByBatchIdAndStoreIdOrderByImageOrderAsc(batchId, storeId)
                .stream().map(MenuImage::getId).toList();
        if (imageIds.isEmpty()) {
            return new BatchResult(batch.getStatus().name(), new MenuBatchResponse(batchId, batch.getBaseMenuRevision(),
                    atSeoul(batch.getUploadedAt()), atSeoul(batch.getSavedAt()), null, List.of()));
        }
        List<MenuImageItem> candidates = itemRepository.findAllByMenuImageIdInOrderBySortOrderAscIdAsc(imageIds);
        List<MenuBatchResponse.DetectedItem> detected = distinctCandidates(candidates);
        return new BatchResult(batch.getStatus().name(), new MenuBatchResponse(batchId, batch.getBaseMenuRevision(),
                atSeoul(batch.getUploadedAt()), atSeoul(batch.getSavedAt()), null, detected));
    }

    public Store requireOwnedStore(long userId, long storeId) {
        Store store = storeRepository.findByOwnerId(userId)
                .orElseThrow(() -> new MenuRequestException(HttpStatus.NOT_FOUND, "매장 정보를 찾을 수 없습니다."));
        if (!store.getId().equals(storeId)) {
            throw new MenuRequestException(HttpStatus.FORBIDDEN, "이 매장의 메뉴를 조회하거나 수정할 권한이 없습니다.");
        }
        return store;
    }

    private MenuListResponse.Item toMenuItem(Menu menu) {
        return new MenuListResponse.Item(menu.getId(), menu.getSortOrder(), menu.getName(),
                menu.getPrice(), menu.getCategory().name(), atSeoul(menu.getUpdatedAt()));
    }

    private MenuListResponse.DraftBatch toDraftBatch(MenuUploadBatch batch) {
        return new MenuListResponse.DraftBatch(batch.getId(), batch.getStatus().name(),
                atSeoul(batch.getUploadedAt()), batch.getFailReason(), atSeoul(batch.getSavedAt()));
    }

    public static OffsetDateTime atSeoul(LocalDateTime time) {
        return time == null ? null : time.atZone(SEOUL).toOffsetDateTime();
    }

    public static List<MenuBatchResponse.DetectedItem> distinctCandidates(List<MenuImageItem> candidates) {
        List<MenuImageItem> sorted = candidates.stream()
                .sorted(Comparator.comparingInt(MenuImageItem::getSortOrder).thenComparing(MenuImageItem::getId))
                .toList();
        Map<String, Integer> positions = new HashMap<>();
        List<MenuBatchResponse.DetectedItem> result = new ArrayList<>();
        for (MenuImageItem item : sorted) {
            String key = candidateKey(item);
            if (key != null && positions.containsKey(key)) {
                int position = positions.get(key);
                MenuBatchResponse.DetectedItem first = result.get(position);
                if (item.isPriceReviewRequired() && !first.priceReviewRequired()) {
                    result.set(position, new MenuBatchResponse.DetectedItem(first.itemId(), first.order(),
                            first.name(), first.price(), first.category(), true));
                }
                continue;
            }
            if (key != null) {
                positions.put(key, result.size());
            }
            result.add(new MenuBatchResponse.DetectedItem(item.getId(), item.getSortOrder(),
                    item.getDetectedName(), item.getDetectedPrice(), item.getDetectedCategory(),
                    item.isPriceReviewRequired()));
        }
        return List.copyOf(result);
    }

    public static String candidateKey(MenuImageItem item) {
        if (item.getDetectedName() == null || item.getDetectedPrice() == null
                || item.getDetectedCategory() == null) {
            return null;
        }
        return Normalizer.normalize(item.getDetectedName().trim().replaceAll("\\s+", " "), Normalizer.Form.NFC)
                + "\u0000" + item.getDetectedPrice() + "\u0000" + item.getDetectedCategory();
    }

    public record BatchResult(String status, MenuBatchResponse data) {
    }
}
