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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SalesUploadServiceTest {

    private SalesFileStorage fileStorage;
    private SalesUploadLifecycleService lifecycleService;
    private SalesUploadProcessingService processingService;
    private StoreOwnershipRepository storeOwnershipRepository;
    private SalesUploadService service;

    @BeforeEach
    void setUp() {
        fileStorage = mock(SalesFileStorage.class);
        lifecycleService = mock(SalesUploadLifecycleService.class);
        processingService = mock(SalesUploadProcessingService.class);
        storeOwnershipRepository = mock(StoreOwnershipRepository.class);
        service = new SalesUploadService(
                fileStorage,
                lifecycleService,
                processingService,
                storeOwnershipRepository
        );
    }

    @Test
    void validatesStoresProcessesFileAndReturnsCompletedReceipt() {
        SalesUploadCommand command = command("sales.xlsx", new byte[]{1, 2, 3});
        SalesUploadReceipt pending = new SalesUploadReceipt(12L, 21L, "PENDING");

        when(storeOwnershipRepository.existsActiveStoreOwnedBy(1L, 7L)).thenReturn(true);
        when(fileStorage.store(any())).thenReturn(new StoredSalesFile("1/ab/file.xlsx"));
        when(lifecycleService.createPending(any(), any(), any(), any(), any())).thenReturn(pending);

        assertThat(service.upload(command)).isEqualTo(
                new SalesUploadReceipt(12L, 21L, "COMPLETED")
        );
        verify(lifecycleService).createPending(any(), any(), any(), any(), any());
        verify(processingService).process(12L);
    }

    @Test
    void rejectsUploadWhenUserIsNotStoreOwner() {
        SalesUploadCommand command = command("sales.xlsx", new byte[]{1, 2, 3});
        when(storeOwnershipRepository.existsActiveStoreOwnedBy(1L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> service.upload(command))
                .isInstanceOf(SalesUploadRequestException.class)
                .hasMessage("매장 소유자만 매출 파일을 업로드할 수 있습니다.");
        verifyNoInteractions(fileStorage, lifecycleService, processingService);
    }

    @Test
    void rejectsFileLargerThanTenMegabytes() {
        SalesUploadCommand command = command("sales.xlsx", new byte[10 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> service.upload(command))
                .isInstanceOf(SalesUploadRequestException.class)
                .hasMessage("파일 크기가 10MB를 초과했습니다.");
        verifyNoInteractions(fileStorage, lifecycleService, processingService, storeOwnershipRepository);
    }

    @Test
    void rejectsUnsupportedExtensionBeforeStoringFile() {
        SalesUploadCommand command = command("sales.xls", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.upload(command))
                .isInstanceOf(SalesUploadRequestException.class)
                .hasMessage("지원하지 않는 파일 형식입니다.");
        verifyNoInteractions(fileStorage, lifecycleService, processingService, storeOwnershipRepository);
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
