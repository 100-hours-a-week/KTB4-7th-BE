package com.memme.service.store.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memme.entity.store.MenuProcessingStatus;
import com.memme.entity.store.MenuUploadBatch;
import com.memme.entity.store.Store;
import com.memme.repository.store.MenuUploadBatchRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.store.recognition.MenuRecognitionAiClient;
import com.memme.service.store.recognition.MenuRecognitionAiException;
import com.memme.service.store.recognition.MenuRecognitionResponse;
import com.memme.service.store.storage.MenuImageStorage;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MenuUploadServiceTest {

    private final StoreRepository stores = mock(StoreRepository.class);
    private final StoreOwnershipRepository ownership = mock(StoreOwnershipRepository.class);
    private final MenuUploadBatchRepository batches = mock(MenuUploadBatchRepository.class);
    private final MenuImageStorage storage = mock(MenuImageStorage.class);
    private final MenuRecognitionAiClient ai = mock(MenuRecognitionAiClient.class);
    private final MenuUploadLifecycleService lifecycle = mock(MenuUploadLifecycleService.class);
    private final MenuUploadService service = new MenuUploadService(
            stores, ownership, batches, storage, ai, lifecycle);

    @BeforeEach
    void owner() {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(7L);
        when(stores.findByOwnerId(3L)).thenReturn(Optional.of(store));
        when(ownership.existsActiveStoreOwnedBy(7L, 3L)).thenReturn(true);
        when(storage.store(any())).thenReturn("menu-images/7/one.jpg");
        when(storage.temporaryReadUrl("menu-images/7/one.jpg"))
                .thenReturn(URI.create("https://images.test/one.jpg?signature=test"));
        when(lifecycle.create(eq(7L), any(), any(), anyList()))
                .thenReturn(new MenuUploadLifecycleService.Creation(15L, true, "hash"));
        when(lifecycle.start(7L, 15L)).thenReturn(List.of(
                new MenuUploadLifecycleService.MenuImageReference(52L, "menu-images/7/one.jpg", 1)));
    }

    @Test
    void 저장한_배치를_AI에_전달하고_최종_응답을_반환한다() {
        when(ai.recognize(any())).thenReturn(new MenuRecognitionResponse("ok",
                new MenuRecognitionResponse.Data(List.of())));
        when(lifecycle.snapshot(7L, 15L)).thenReturn(snapshot(MenuProcessingStatus.COMPLETED, null));

        MenuUploadResult result = service.upload(3L, "request-one", List.of(jpeg()));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.data().batchId()).isEqualTo(15);
        assertThat(result.data().imageCount()).isEqualTo(1);
        verify(lifecycle).complete(eq(7L), eq(15L), any());
        var request = org.mockito.ArgumentCaptor.forClass(
                com.memme.service.store.recognition.MenuRecognitionRequest.class);
        verify(ai).recognize(request.capture());
        assertThat(request.getValue().images().getFirst().imageId()).isEqualTo(52L);
        assertThat(request.getValue().images().getFirst().imageUrl())
                .startsWith("https://images.test/");
    }

    @Test
    void AI_실패는_업로드를_삭제하지_않고_실패_사유를_남긴다() {
        when(ai.recognize(any())).thenThrow(new MenuRecognitionAiException(
                "LOW_IMAGE_QUALITY", "quality"));
        when(lifecycle.snapshot(7L, 15L)).thenReturn(
                snapshot(MenuProcessingStatus.FAILED, "LOW_IMAGE_QUALITY"));

        MenuUploadResult result = service.upload(3L, "request-one", List.of(jpeg()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.data().failReason()).isEqualTo("LOW_IMAGE_QUALITY");
        verify(lifecycle).fail(7L, 15L, "LOW_IMAGE_QUALITY");
        verify(storage, never()).delete(any());
    }

    @Test
    void 잘못된_이미지와_11장은_저장전에_거부한다() {
        assertThatThrownBy(() -> service.upload(3L, "key", List.of(
                new MenuUploadFile("fake.jpg", "image/jpeg", new byte[] {1, 2, 3}))))
                .isInstanceOfSatisfying(MenuUploadException.class,
                        error -> assertThat(error.getCode()).isEqualTo("UNSUPPORTED_IMAGE_TYPE"));
        assertThatThrownBy(() -> service.upload(3L, "key", java.util.Collections.nCopies(11, jpeg())))
                .isInstanceOfSatisfying(MenuUploadException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_IMAGE_COUNT"));
        verify(storage, never()).store(any());
    }

    @Test
    void 저장_후_DB_오류가_나면_이미지를_정리한다() {
        when(lifecycle.create(eq(7L), any(), any(), anyList()))
                .thenThrow(new IllegalStateException("db failed"));

        assertThatThrownBy(() -> service.upload(3L, "key", List.of(jpeg())))
                .isInstanceOf(IllegalStateException.class);

        verify(storage).delete("menu-images/7/one.jpg");
        verify(ai, never()).recognize(any());
    }

    @Test
    void 같은_키와_이미지의_재요청은_기존_완료_배치를_반환한다() {
        when(ai.recognize(any())).thenReturn(new MenuRecognitionResponse("ok",
                new MenuRecognitionResponse.Data(List.of())));
        when(lifecycle.snapshot(7L, 15L)).thenReturn(snapshot(MenuProcessingStatus.COMPLETED, null));
        var hashes = org.mockito.ArgumentCaptor.forClass(String.class);

        service.upload(3L, "same-key", List.of(jpeg()));
        verify(lifecycle).create(eq(7L), any(), hashes.capture(), anyList());
        MenuUploadBatch existing = mock(MenuUploadBatch.class);
        when(existing.getId()).thenReturn(15L);
        when(existing.getRequestHash()).thenReturn(hashes.getValue());
        when(batches.findByStoreIdAndIdempotencyKeyHash(eq(7L), any()))
                .thenReturn(Optional.of(existing));

        MenuUploadResult result = service.upload(3L, "same-key", List.of(jpeg()));

        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(storage).store(any());

        assertThatThrownBy(() -> service.upload(3L, "same-key", List.of(
                new MenuUploadFile("other.jpg", "image/jpeg",
                        new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}))))
                .isInstanceOfSatisfying(MenuUploadException.class,
                        error -> assertThat(error.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    private MenuUploadFile jpeg() {
        return new MenuUploadFile("board.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9});
    }

    private MenuUploadLifecycleService.Snapshot snapshot(MenuProcessingStatus status, String reason) {
        return new MenuUploadLifecycleService.Snapshot(15L, status, reason,
                LocalDateTime.of(2026, 10, 13, 10, 0), 1);
    }
}
