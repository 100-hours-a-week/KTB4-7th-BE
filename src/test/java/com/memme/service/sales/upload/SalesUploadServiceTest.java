package com.memme.service.sales.upload;

import com.memme.exception.sales.SalesUploadRequestException;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.service.sales.storage.SalesFileStorage;
import com.memme.service.sales.storage.StoredSalesFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesUploadServiceTest {

    private SalesFileStorage fileStorage;
    private SalesUploadLifecycleService lifecycleService;
    private SalesUploadWorker worker;
    private StoreOwnershipRepository storeOwnershipRepository;
    private SalesUploadService service;

    @BeforeEach
    void setUp() {
        fileStorage = mock(SalesFileStorage.class);
        lifecycleService = mock(SalesUploadLifecycleService.class);
        worker = mock(SalesUploadWorker.class);
        storeOwnershipRepository = mock(StoreOwnershipRepository.class);
        service = new SalesUploadService(
                fileStorage,
                lifecycleService,
                worker,
                storeOwnershipRepository
        );
    }

    @Test
    void storesFileAndSchedulesWorkerBeforeReturningPendingReceipt() {
        SalesUploadCommand command = command("sales.xlsx", new byte[]{1, 2, 3});
        SalesUploadReceipt pending = new SalesUploadReceipt(12L, 21L, "PENDING");

        when(storeOwnershipRepository.existsActiveStoreOwnedBy(1L, 7L)).thenReturn(true);
        when(fileStorage.store(any())).thenReturn(new StoredSalesFile("1/ab/file.xlsx"));
        when(lifecycleService.createPending(any(), any(), any(), any(), any())).thenReturn(pending);

        assertThat(service.upload(command)).isEqualTo(
                pending
        );
        var order = inOrder(fileStorage, lifecycleService, worker);
        order.verify(fileStorage).store(any());
        order.verify(lifecycleService).createPending(any(), any(), any(), any(), any());
        order.verify(worker).process(12L);
    }

    @Test
    void rejectsUploadWhenUserIsNotStoreOwner() {
        SalesUploadCommand command = command("sales.xlsx", new byte[]{1, 2, 3});
        when(storeOwnershipRepository.existsActiveStoreOwnedBy(1L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.upload(command))
                .isInstanceOf(SalesUploadRequestException.class)
                .hasMessage("매장 소유자만 매출 파일을 업로드할 수 있습니다.");
        verifyNoInteractions(fileStorage, lifecycleService, worker);
    }

    @Test
    void rejectsFileLargerThanTenMegabytes() {
        SalesUploadCommand command = command("sales.xlsx", new byte[10 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.upload(command))
                .isInstanceOf(SalesUploadRequestException.class)
                .hasMessage("파일 크기가 10MB를 초과했습니다.");
        verifyNoInteractions(fileStorage, lifecycleService, worker, storeOwnershipRepository);
    }

    @Test
    void rejectsUnsupportedExtensionBeforeStoringFile() {
        SalesUploadCommand command = command("sales.xls", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.upload(command))
                .isInstanceOf(SalesUploadRequestException.class)
                .hasMessage("지원하지 않는 파일 형식입니다.");
        verifyNoInteractions(fileStorage, lifecycleService, worker, storeOwnershipRepository);
    }

    @Test
    void recordsFailureAndRejectsReceiptWhenWorkerQueueIsFull() {
        when(storeOwnershipRepository.existsActiveStoreOwnedBy(1L, 7L)).thenReturn(true);
        when(fileStorage.store(any())).thenReturn(new StoredSalesFile("1/file.xlsx"));
        when(lifecycleService.createPending(any(), any(), any(), any(), any()))
                .thenReturn(new SalesUploadReceipt(12L, 21L, "PENDING"));
        doThrow(new java.util.concurrent.RejectedExecutionException("full"))
                .when(worker).process(12L);

        assertThatThrownBy(() -> service.upload(command("sales.xlsx", new byte[]{1})))
                .isInstanceOf(com.memme.exception.sales.SalesUploadProcessingException.class);
        verify(lifecycleService).markFailed(12L, "UPLOAD_PROCESSING_ERROR",
                "업로드 처리 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    void doesNotStartWorkerWhenPendingTransactionFails() {
        when(storeOwnershipRepository.existsActiveStoreOwnedBy(1L, 7L)).thenReturn(true);
        when(fileStorage.store(any())).thenReturn(new StoredSalesFile("1/file.xlsx"));
        when(lifecycleService.createPending(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("DB failure"));

        assertThatThrownBy(() -> service.upload(command("sales.xlsx", new byte[]{1})))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(worker);
    }

    @Test
    void outboxReceiptDoesNotDependOnExecutorAvailability() {
        when(storeOwnershipRepository.existsActiveStoreOwnedBy(1L, 7L)).thenReturn(true);
        when(fileStorage.store(any())).thenReturn(new StoredSalesFile("1/file.xlsx"));
        when(lifecycleService.createPending(any(), any(), any(), any(), any()))
                .thenReturn(new SalesUploadReceipt(12L, 21L, "PENDING"));
        when(lifecycleService.usesOutbox()).thenReturn(true);

        assertThat(service.upload(command("sales.xlsx", new byte[]{1})).status()).isEqualTo("PENDING");
        verifyNoInteractions(worker);
        verify(lifecycleService, never()).markFailed(any(), any(), any());
    }

    private SalesUploadCommand command(String fileName, byte[] content) {
        return new SalesUploadCommand(
                1L,
                7L,
                fileName,
                content
        );
    }
}
