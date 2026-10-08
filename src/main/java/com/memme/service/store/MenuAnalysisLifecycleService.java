package com.memme.service.store;

import com.memme.entity.store.MenuImage;
import com.memme.entity.store.MenuImageItem;
import com.memme.entity.store.MenuUploadBatch;
import com.memme.repository.store.MenuImageItemRepository;
import com.memme.repository.store.MenuImageRepository;
import com.memme.repository.store.MenuUploadBatchRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MenuAnalysisLifecycleService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MenuUploadBatchRepository batchRepository;
    private final MenuImageRepository imageRepository;
    private final MenuImageItemRepository itemRepository;
    private final JsonMapper mapper;

    public MenuAnalysisLifecycleService(MenuUploadBatchRepository batchRepository,
            MenuImageRepository imageRepository, MenuImageItemRepository itemRepository, JsonMapper mapper) {
        this.batchRepository = batchRepository;
        this.imageRepository = imageRepository;
        this.itemRepository = itemRepository;
        this.mapper = mapper;
    }

    @Transactional
    public List<MenuImage> begin(long storeId, long batchId) {
        MenuUploadBatch batch = batchRepository.findByIdAndStoreId(batchId, storeId).orElseThrow();
        List<MenuImage> images = imageRepository.findAllByBatchIdAndStoreIdOrderByImageOrderAsc(batchId, storeId);
        batch.startProcessing();
        images.forEach(MenuImage::startProcessing);
        return images;
    }

    @Transactional
    public void complete(long storeId, long batchId, List<MenuRecognitionAiClient.DetectedItem> detected) {
        MenuUploadBatch batch = batchRepository.findByIdAndStoreId(batchId, storeId).orElseThrow();
        List<MenuImage> images = imageRepository.findAllByBatchIdAndStoreIdOrderByImageOrderAsc(batchId, storeId);
        Set<Long> imageIds = images.stream().map(MenuImage::getId).collect(Collectors.toSet());
        if (detected.stream().anyMatch(item -> !imageIds.contains(item.sourceImageId()))) {
            throw new IllegalStateException("AI 응답의 이미지 ID가 현재 배치와 다릅니다.");
        }
        Map<Long, List<MenuRecognitionAiClient.DetectedItem>> byImage = detected.stream()
                .collect(Collectors.groupingBy(MenuRecognitionAiClient.DetectedItem::sourceImageId));
        LocalDateTime now = LocalDateTime.now(SEOUL);
        int order = 1;
        for (MenuImage image : images) {
            List<MenuRecognitionAiClient.DetectedItem> items = byImage.getOrDefault(image.getId(), List.of());
            image.complete(mapper.writeValueAsString(items));
            for (MenuRecognitionAiClient.DetectedItem item : items) {
                if (item.name() == null || item.name().length() > 100
                        || (item.category() != null && item.category().length() > 50)
                        || (item.confidence() != null && (item.confidence().signum() < 0
                        || item.confidence().compareTo(java.math.BigDecimal.ONE) > 0))) {
                    throw new IllegalStateException("AI 메뉴 후보 형식이 올바르지 않습니다.");
                }
                Long price = item.price();
                boolean invalidPrice = price == null || price < 0 || price > 1_000_000;
                itemRepository.save(MenuImageItem.pending(image.getId(), item.name(), item.category(),
                        invalidPrice ? null : price, order++, item.confidence(),
                        item.priceReviewRequired() || invalidPrice, now));
            }
        }
        batch.complete(now);
    }

    @Transactional
    public void fail(long storeId, long batchId, String reason) {
        MenuUploadBatch batch = batchRepository.findByIdAndStoreId(batchId, storeId).orElseThrow();
        if (batch.getStatus() == com.memme.entity.store.MenuProcessingStatus.COMPLETED) {
            return;
        }
        imageRepository.findAllByBatchIdAndStoreIdOrderByImageOrderAsc(batchId, storeId).stream()
                .filter(image -> image.getOcrStatus() != com.memme.entity.store.MenuProcessingStatus.COMPLETED)
                .forEach(MenuImage::fail);
        batch.fail(reason, LocalDateTime.now(SEOUL));
    }
}
