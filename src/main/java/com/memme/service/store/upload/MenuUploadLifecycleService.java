package com.memme.service.store.upload;

import com.memme.entity.store.MenuImage;
import com.memme.entity.store.MenuImageItem;
import com.memme.entity.store.MenuProcessingStatus;
import com.memme.entity.store.MenuUploadBatch;
import com.memme.entity.store.Store;
import com.memme.repository.store.MenuImageItemRepository;
import com.memme.repository.store.MenuImageRepository;
import com.memme.repository.store.MenuUploadBatchRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.store.recognition.MenuRecognitionResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MenuUploadLifecycleService {

    private final StoreRepository stores;
    private final MenuUploadBatchRepository batches;
    private final MenuImageRepository images;
    private final MenuImageItemRepository items;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public MenuUploadLifecycleService(StoreRepository stores, MenuUploadBatchRepository batches,
            MenuImageRepository images, MenuImageItemRepository items, Clock clock) {
        this.stores = stores;
        this.batches = batches;
        this.images = images;
        this.items = items;
        this.clock = clock;
    }

    @Transactional
    public Creation create(Long storeId, String keyHash, String requestHash,
            List<StoredMenuUploadFile> files) {
        Store store = stores.findForMenuUpdate(storeId).orElseThrow();
        var existing = batches.findByStoreIdAndIdempotencyKeyHash(storeId, keyHash);
        if (existing.isPresent()) {
            return new Creation(existing.get().getId(), false, existing.get().getRequestHash());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        MenuUploadBatch previous = batches.findFirstByStoreIdOrderByUploadedAtDescIdDesc(storeId)
                .orElse(null);
        MenuUploadBatch batch = batches.saveAndFlush(MenuUploadBatch.pending(
                storeId, store.getMenuRevision(), keyHash, requestHash, now));
        for (StoredMenuUploadFile file : files) {
            images.save(MenuImage.pending(batch.getId(), storeId, file.order(), file.storageKey(),
                    file.fileSizeBytes(), file.mimeType(), now));
        }
        if (previous != null && previous.getSavedAt() == null
                && previous.getSupersededAt() == null) {
            previous.markSuperseded(now);
        }
        return new Creation(batch.getId(), true, requestHash);
    }

    @Transactional
    public List<MenuImageReference> start(Long storeId, Long batchId) {
        MenuUploadBatch batch = findBatch(storeId, batchId);
        batch.startProcessing();
        List<MenuImageReference> references = new ArrayList<>();
        for (MenuImage image : findImages(storeId, batchId)) {
            image.startProcessing();
            references.add(new MenuImageReference(image.getId(), image.getStorageKey(), image.getImageOrder()));
        }
        return references;
    }

    @Transactional
    public void complete(Long storeId, Long batchId, MenuRecognitionResponse response) {
        MenuUploadBatch batch = findBatch(storeId, batchId);
        List<MenuImage> batchImages = findImages(storeId, batchId);
        Map<Long, MenuImage> byId = batchImages.stream()
                .collect(Collectors.toMap(MenuImage::getId, Function.identity()));
        var grouped = response.data().detectedItems().stream()
                .collect(Collectors.groupingBy(MenuRecognitionResponse.DetectedItem::sourceImageId));
        Map<Long, String> failed = response.data().failedImages().stream()
                .collect(Collectors.toMap(MenuRecognitionResponse.FailedImage::sourceImageId,
                        MenuRecognitionResponse.FailedImage::failReason));
        if (!byId.keySet().containsAll(grouped.keySet()) || !byId.keySet().containsAll(failed.keySet())
                || grouped.keySet().stream().anyMatch(failed::containsKey)
                || failed.size() == batchImages.size()) {
            throw new IllegalArgumentException("AI 응답에 다른 배치 이미지가 포함되어 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (MenuImage image : batchImages) {
            String failReason = failed.get(image.getId());
            if (failReason != null) {
                image.fail(json.writeValueAsString(Map.of("failReason", failReason)));
                continue;
            }
            List<MenuRecognitionResponse.DetectedItem> detected = grouped.getOrDefault(image.getId(), List.of());
            image.complete(json.writeValueAsString(detected));
            for (int order = 0; order < detected.size(); order++) {
                MenuRecognitionResponse.DetectedItem item = detected.get(order);
                Long price = item.price();
                boolean invalidPrice = price == null || price < 0 || price > 1_000_000;
                BigDecimal confidence = item.confidence() == null ? null
                        : item.confidence().setScale(4, RoundingMode.HALF_UP);
                items.save(MenuImageItem.pending(image.getId(), item.name(), item.category(),
                        invalidPrice ? null : price, order, confidence,
                        invalidPrice || item.priceReviewRequired(), now));
            }
        }
        batch.complete(now);
    }

    @Transactional
    public void fail(Long storeId, Long batchId, String reason) {
        MenuUploadBatch batch = findBatch(storeId, batchId);
        if (batch.getStatus() == MenuProcessingStatus.COMPLETED || batch.getStatus() == MenuProcessingStatus.FAILED) {
            return;
        }
        batch.fail(reason, LocalDateTime.now(clock));
        for (MenuImage image : findImages(storeId, batchId)) {
            image.fail(json.writeValueAsString(Map.of("failReason", reason)));
        }
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(Long storeId, Long batchId) {
        MenuUploadBatch batch = findBatch(storeId, batchId);
        return new Snapshot(batch.getId(), batch.getStatus(), batch.getFailReason(),
                batch.getUploadedAt(), findImages(storeId, batchId).size());
    }

    private MenuUploadBatch findBatch(Long storeId, Long batchId) {
        return batches.findByIdAndStoreId(batchId, storeId).orElseThrow();
    }

    private List<MenuImage> findImages(Long storeId, Long batchId) {
        return images.findAllByBatchIdAndStoreIdOrderByImageOrderAsc(batchId, storeId);
    }

    public record Creation(Long batchId, boolean created, String requestHash) {}
    public record MenuImageReference(Long imageId, String storageKey, int order) {}
    public record Snapshot(Long batchId, MenuProcessingStatus status, String failReason,
            LocalDateTime uploadedAt, int imageCount) {}
}
