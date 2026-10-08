package com.memme.service.store;

import com.memme.dto.store.MenuBatchResponse;
import com.memme.dto.store.MenuConfirmationRequest;
import com.memme.dto.store.MenuConfirmationResponse;
import com.memme.dto.store.MenuFieldError;
import com.memme.dto.store.MenuPatchRequest;
import com.memme.dto.store.MenuPatchResponse;
import com.memme.entity.store.Menu;
import com.memme.entity.store.MenuCategory;
import com.memme.entity.store.MenuImage;
import com.memme.entity.store.MenuImageItem;
import com.memme.entity.store.MenuProcessingStatus;
import com.memme.entity.store.MenuUploadBatch;
import com.memme.entity.store.MenuWriteOperation;
import com.memme.entity.store.MenuWriteRequest;
import com.memme.entity.store.Store;
import com.memme.exception.store.MenuRequestException;
import com.memme.repository.store.MenuImageItemRepository;
import com.memme.repository.store.MenuImageRepository;
import com.memme.repository.store.MenuRepository;
import com.memme.repository.store.MenuUploadBatchRepository;
import com.memme.repository.store.MenuWriteRequestRepository;
import com.memme.repository.store.StoreRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MenuWriteService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final MenuUploadBatchRepository batchRepository;
    private final MenuImageRepository imageRepository;
    private final MenuImageItemRepository itemRepository;
    private final MenuWriteRequestRepository requestRepository;
    private final MenuQueryService queryService;
    private final JsonMapper mapper;

    public MenuWriteService(StoreRepository storeRepository, MenuRepository menuRepository,
            MenuUploadBatchRepository batchRepository, MenuImageRepository imageRepository,
            MenuImageItemRepository itemRepository, MenuWriteRequestRepository requestRepository,
            MenuQueryService queryService, JsonMapper mapper) {
        this.storeRepository = storeRepository;
        this.menuRepository = menuRepository;
        this.batchRepository = batchRepository;
        this.imageRepository = imageRepository;
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.queryService = queryService;
        this.mapper = mapper;
    }

    @Transactional
    public MenuPatchResponse patch(long userId, long storeId, String idempotencyKey, MenuPatchRequest request) {
        Store store = lockOwnedStore(userId, storeId);
        String bodyHash = hash(mapper.writeValueAsString(request));
        MenuPatchResponse replay = replay(storeId, MenuWriteOperation.MENU_PATCH,
                idempotencyKey, bodyHash, MenuPatchResponse.class);
        if (replay != null) {
            return replay;
        }
        requireRevision(store, request.expectedMenuRevision());
        List<Menu> current = menuRepository.findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(storeId);
        if (request.items() == null || request.items().size() != current.size()) {
            throw new MenuRequestException(HttpStatus.BAD_REQUEST, "현재 메뉴 전체를 입력해 주세요.");
        }
        Map<Long, Menu> currentById = new HashMap<>();
        current.forEach(menu -> currentById.put(menu.getId(), menu));
        Set<Long> seenIds = new HashSet<>();
        List<ValidatedItem> desired = new ArrayList<>();
        List<MenuFieldError> errors = new ArrayList<>();
        for (MenuPatchRequest.Item item : request.items()) {
            if (item == null || item.menuId() == null || !seenIds.add(item.menuId())) {
                throw new MenuRequestException(HttpStatus.BAD_REQUEST, "메뉴 ID 목록을 확인해 주세요.");
            }
            Menu existing = currentById.get(item.menuId());
            if (existing == null) {
                throw new MenuRequestException(HttpStatus.FORBIDDEN, "이 매장의 메뉴가 아닙니다.");
            }
            desired.add(validate(item.menuId(), null, item.name(), item.price(),
                    item.category(), item.order(), errors));
        }
        rejectErrors(errors);
        rejectDuplicates(desired);
        boolean changed = false;
        for (int i = 0; i < desired.size(); i++) {
            if (!same(currentById.get(request.items().get(i).menuId()), desired.get(i))) {
                changed = true;
                break;
            }
        }
        if (!changed) {
            throw new MenuRequestException(HttpStatus.BAD_REQUEST, "변경된 메뉴 정보가 없습니다.");
        }
        LocalDateTime now = LocalDateTime.now(SEOUL);
        for (int i = 0; i < desired.size(); i++) {
            Menu old = currentById.get(request.items().get(i).menuId());
            if (!same(old, desired.get(i))) {
                old.remove(now);
            }
        }
        menuRepository.flush();
        List<MenuPatchResponse.Item> saved = new ArrayList<>();
        for (int i = 0; i < desired.size(); i++) {
            ValidatedItem value = desired.get(i);
            Menu old = currentById.get(request.items().get(i).menuId());
            Menu menu = same(old, value) ? old : menuRepository.save(Menu.create(
                    storeId, value.name(), value.category(), value.price(), value.order(), now));
            saved.add(new MenuPatchResponse.Item(menu.getId(), menu.getSortOrder(),
                    menu.getName(), menu.getPrice(), menu.getCategory().name()));
        }
        store.advanceMenuRevision(request.expectedMenuRevision(), now);
        MenuPatchResponse response = new MenuPatchResponse(store.getMenuRevision(),
                saved.stream().sorted(Comparator.comparingInt(MenuPatchResponse.Item::order)).toList());
        recordResponse(storeId, MenuWriteOperation.MENU_PATCH, idempotencyKey, bodyHash, response, now);
        return response;
    }

    @Transactional
    public MenuConfirmationResponse confirm(long userId, long storeId, long batchId,
            String idempotencyKey, MenuConfirmationRequest request) {
        Store store = lockOwnedStore(userId, storeId);
        String bodyHash = hash(batchId + ":" + mapper.writeValueAsString(request));
        MenuConfirmationResponse replay = replay(storeId, MenuWriteOperation.MENU_CONFIRM,
                idempotencyKey, bodyHash, MenuConfirmationResponse.class);
        if (replay != null) {
            return replay;
        }
        requireRevision(store, request.expectedMenuRevision());
        MenuUploadBatch batch = batchRepository.findByIdAndStoreId(batchId, storeId)
                .orElseThrow(() -> new MenuRequestException(HttpStatus.NOT_FOUND, "메뉴판 분석 내역을 찾을 수 없습니다."));
        MenuUploadBatch latest = batchRepository.findFirstByStoreIdOrderByUploadedAtDescIdDesc(storeId)
                .orElseThrow(() -> new MenuRequestException(HttpStatus.NOT_FOUND, "메뉴판 분석 내역을 찾을 수 없습니다."));
        if (!latest.getId().equals(batchId) || batch.getSavedAt() != null
                || batch.getSupersededAt() != null || batch.getStatus() != MenuProcessingStatus.COMPLETED
                || batch.getBaseMenuRevision() != store.getMenuRevision()) {
            throw new MenuRequestException(HttpStatus.CONFLICT, "저장할 수 없는 메뉴판 분석 결과입니다.");
        }
        List<Long> imageIds = imageRepository.findAllByBatchIdAndStoreIdOrderByImageOrderAsc(batchId, storeId)
                .stream().map(MenuImage::getId).toList();
        List<MenuImageItem> raw = imageIds.isEmpty() ? List.of()
                : itemRepository.findAllByMenuImageIdInOrderBySortOrderAscIdAsc(imageIds);
        List<MenuBatchResponse.DetectedItem> displayed = MenuQueryService.distinctCandidates(raw);
        if (displayed.isEmpty()) {
            throw new MenuRequestException(HttpStatus.BAD_REQUEST, "저장할 메뉴 후보가 없습니다.");
        }
        if (request.items() == null || request.items().size() != displayed.size()) {
            throw new MenuRequestException(HttpStatus.BAD_REQUEST, "추출된 메뉴 전체를 입력해 주세요.");
        }
        Map<Long, MenuBatchResponse.DetectedItem> displayedById = new HashMap<>();
        displayed.forEach(item -> displayedById.put(item.itemId(), item));
        Set<Long> seenIds = new HashSet<>();
        List<ValidatedItem> desired = new ArrayList<>();
        List<MenuFieldError> errors = new ArrayList<>();
        for (MenuConfirmationRequest.Item item : request.items()) {
            if (item == null || item.itemId() == null || !seenIds.add(item.itemId())) {
                throw new MenuRequestException(HttpStatus.BAD_REQUEST, "후보 ID 목록을 확인해 주세요.");
            }
            MenuBatchResponse.DetectedItem detected = displayedById.get(item.itemId());
            if (detected == null) {
                throw new MenuRequestException(HttpStatus.BAD_REQUEST, "이 배치의 메뉴 후보가 아닙니다.");
            }
            ValidatedItem value = validate(null, item.itemId(), item.name(), item.price(),
                    item.category(), item.order(), errors);
            desired.add(value);
            if (detected.priceReviewRequired() && detected.price() != null
                    && detected.price().equals(item.price()) && !Boolean.TRUE.equals(item.priceConfirmed())) {
                errors.add(new MenuFieldError(null, item.itemId(), "price", "PRICE_REVIEW_REQUIRED",
                        "가격 인식 결과를 확인해주세요."));
            }
        }
        rejectErrors(errors);
        rejectDuplicates(desired);
        LocalDateTime now = LocalDateTime.now(SEOUL);
        menuRepository.findAllByStoreIdAndRemovedAtIsNullOrderBySortOrderAscIdAsc(storeId)
                .forEach(menu -> menu.remove(now));
        menuRepository.flush();
        List<MenuConfirmationResponse.Item> saved = new ArrayList<>();
        for (int i = 0; i < desired.size(); i++) {
            ValidatedItem value = desired.get(i);
            Menu menu = menuRepository.save(Menu.create(storeId, value.name(), value.category(),
                    value.price(), value.order(), now));
            saved.add(new MenuConfirmationResponse.Item(request.items().get(i).itemId(),
                    menu.getId(), menu.getName(), menu.getPrice(), menu.getCategory().name()));
        }
        Map<Long, Long> savedByCandidate = new HashMap<>();
        saved.forEach(item -> savedByCandidate.put(item.itemId(), item.menuId()));
        Map<String, Long> savedByOriginalTuple = new HashMap<>();
        for (MenuImageItem item : raw) {
            Long savedMenuId = savedByCandidate.get(item.getId());
            String key = MenuQueryService.candidateKey(item);
            if (savedMenuId != null && key != null) {
                savedByOriginalTuple.put(key, savedMenuId);
            }
        }
        for (MenuImageItem item : raw) {
            Long menuId = savedByCandidate.get(item.getId());
            if (menuId == null) {
                menuId = savedByOriginalTuple.get(MenuQueryService.candidateKey(item));
            }
            if (menuId != null) {
                item.confirm(menuId, now);
            } else if (item.getStatus() == com.memme.entity.store.MenuImageItemStatus.PENDING) {
                item.reject(now);
            }
        }
        batch.markSaved(now);
        store.advanceMenuRevision(request.expectedMenuRevision(), now);
        MenuConfirmationResponse response = new MenuConfirmationResponse(batchId, store.getMenuRevision(),
                MenuQueryService.atSeoul(now), List.copyOf(saved));
        recordResponse(storeId, MenuWriteOperation.MENU_CONFIRM, idempotencyKey, bodyHash, response, now);
        return response;
    }

    private Store lockOwnedStore(long userId, long storeId) {
        queryService.requireOwnedStore(userId, storeId);
        return storeRepository.findForMenuUpdate(storeId)
                .orElseThrow(() -> new MenuRequestException(HttpStatus.NOT_FOUND, "매장 정보를 찾을 수 없습니다."));
    }

    private void requireRevision(Store store, Long expected) {
        if (expected == null || expected < 0 || store.getMenuRevision() != expected) {
            throw new MenuRequestException(HttpStatus.CONFLICT, "메뉴 개정 번호가 일치하지 않습니다.");
        }
    }

    private ValidatedItem validate(Long menuId, Long itemId, String name, Long price,
            String category, Integer order, List<MenuFieldError> errors) {
        String normalized = name == null ? "" : Normalizer.normalize(
                name.trim().replaceAll("\\s+", " "), Normalizer.Form.NFC);
        if (normalized.isBlank() || normalized.length() > 100) {
            errors.add(new MenuFieldError(menuId, itemId, "name", "INVALID_NAME", "메뉴명을 입력해 주세요."));
        }
        if (price == null || price < 0 || price > 1_000_000) {
            errors.add(new MenuFieldError(menuId, itemId, "price", "OUT_OF_RANGE",
                    "가격은 0원 이상 1,000,000원 이하로 입력해 주세요."));
        }
        MenuCategory parsedCategory = null;
        try {
            parsedCategory = MenuCategory.valueOf(category == null ? "" : category);
        } catch (IllegalArgumentException ignored) {
            errors.add(new MenuFieldError(menuId, itemId, "category", "INVALID_CATEGORY",
                    "카테고리를 선택해 주세요."));
        }
        if (order == null || order < 1) {
            errors.add(new MenuFieldError(menuId, itemId, "order", "INVALID_ORDER",
                    "메뉴 순서를 확인해 주세요."));
        }
        return new ValidatedItem(normalized, price, parsedCategory, order);
    }

    private void rejectErrors(List<MenuFieldError> errors) {
        if (!errors.isEmpty()) {
            throw new MenuRequestException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "입력값을 확인해 주세요.", Map.of("fieldErrors", List.copyOf(errors)));
        }
    }

    private void rejectDuplicates(List<ValidatedItem> desired) {
        Set<String> tuples = new HashSet<>();
        for (ValidatedItem item : desired) {
            String tuple = item.name() + "\u0000" + item.price() + "\u0000" + item.category();
            if (!tuples.add(tuple)) {
                throw new MenuRequestException(HttpStatus.CONFLICT,
                        "중복된 메뉴 정보가 있습니다. 메뉴명, 가격 또는 카테고리를 확인해주세요.",
                        Map.of("code", "DUPLICATE_MENU"));
            }
        }
    }

    private boolean same(Menu menu, ValidatedItem item) {
        return menu.getNormalizedName().equals(item.name()) && menu.getPrice() == item.price()
                && menu.getCategory() == item.category() && menu.getSortOrder() == item.order();
    }

    private <T> T replay(long storeId, MenuWriteOperation operation, String key,
            String requestHash, Class<T> responseType) {
        if (key == null || key.isBlank()) {
            throw new MenuRequestException(HttpStatus.BAD_REQUEST, "Idempotency-Key를 입력해 주세요.");
        }
        return requestRepository.findByStoreIdAndOperationAndKeyHash(storeId, operation, hash(key))
                .map(saved -> {
                    if (!saved.getRequestHash().equals(requestHash)) {
                        throw new MenuRequestException(HttpStatus.CONFLICT,
                                "같은 멱등 키로 다른 메뉴 정보를 저장할 수 없습니다.");
                    }
                    return mapper.readValue(saved.getResponseJson(), responseType);
                }).orElse(null);
    }

    private void recordResponse(long storeId, MenuWriteOperation operation, String key,
            String requestHash, Object response, LocalDateTime now) {
        requestRepository.save(MenuWriteRequest.create(storeId, operation, hash(key),
                requestHash, mapper.writeValueAsString(response), now));
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private record ValidatedItem(String name, Long price, MenuCategory category, Integer order) {
    }
}
