package com.memme.service.store.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.entity.store.MenuImage;
import com.memme.entity.store.MenuImageItem;
import com.memme.entity.store.MenuProcessingStatus;
import com.memme.entity.store.MenuUploadBatch;
import com.memme.repository.store.MenuImageItemRepository;
import com.memme.repository.store.MenuImageRepository;
import com.memme.repository.store.MenuUploadBatchRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.store.recognition.MenuRecognitionResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class MenuUploadLifecycleServiceTest {

    @Test
    void AI_후보를_이미지별로_저장하고_가격_범위를_넘으면_검토_대상으로_남긴다() {
        StoreRepository stores = mock(StoreRepository.class);
        MenuUploadBatchRepository batches = mock(MenuUploadBatchRepository.class);
        MenuImageRepository images = mock(MenuImageRepository.class);
        MenuImageItemRepository items = mock(MenuImageItemRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        MenuUploadLifecycleService lifecycle = new MenuUploadLifecycleService(
                stores, batches, images, items, clock);
        MenuUploadBatch batch = MenuUploadBatch.pending(7L, 0, "key", "request",
                LocalDateTime.of(2026, 10, 13, 9, 0));
        ReflectionTestUtils.setField(batch, "id", 15L);
        batch.startProcessing();
        MenuImage image = MenuImage.pending(15L, 7L, 1, "menu-images/7/one.jpg", 4,
                "image/jpeg", LocalDateTime.of(2026, 10, 13, 9, 0));
        ReflectionTestUtils.setField(image, "id", 52L);
        image.startProcessing();
        when(batches.findByIdAndStoreId(15L, 7L)).thenReturn(Optional.of(batch));
        when(images.findAllByBatchIdAndStoreIdOrderByImageOrderAsc(15L, 7L))
                .thenReturn(List.of(image));

        lifecycle.complete(7L, 15L, new MenuRecognitionResponse("ok",
                new MenuRecognitionResponse.Data(List.of(
                        new MenuRecognitionResponse.DetectedItem(52L, "커피", 1_000_001L,
                                "DRINK", new BigDecimal("0.98765"), false)))));

        ArgumentCaptor<MenuImageItem> candidate = ArgumentCaptor.forClass(MenuImageItem.class);
        verify(items).save(candidate.capture());
        assertThat(candidate.getValue().getDetectedPrice()).isNull();
        assertThat(candidate.getValue().isPriceReviewRequired()).isTrue();
        assertThat(candidate.getValue().getConfidence()).isEqualByComparingTo("0.9877");
        assertThat(image.getOcrResult()).contains("커피");
        assertThat(image.getOcrStatus()).isEqualTo(MenuProcessingStatus.COMPLETED);
        assertThat(batch.getStatus()).isEqualTo(MenuProcessingStatus.COMPLETED);
    }
}
