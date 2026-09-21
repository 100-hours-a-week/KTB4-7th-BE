package com.memme.service.sales.upload;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import com.memme.exception.SalesUploadRequestException;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.service.sales.storage.SalesFile;
import com.memme.service.sales.storage.SalesFileStorage;
import com.memme.service.sales.storage.StoredSalesFile;
import org.springframework.stereotype.Service;

@Service
public class SalesUploadService {

    static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    private final SalesFileStorage fileStorage;
    private final SalesUploadLifecycleService lifecycleService;
    private final SalesUploadProcessingService processingService;
    private final StoreOwnershipRepository storeOwnershipRepository;

    public SalesUploadService(
            SalesFileStorage fileStorage,
            SalesUploadLifecycleService lifecycleService,
            SalesUploadProcessingService processingService,
            StoreOwnershipRepository storeOwnershipRepository
    ) {
        this.fileStorage = fileStorage;
        this.lifecycleService = lifecycleService;
        this.processingService = processingService;
        this.storeOwnershipRepository = storeOwnershipRepository;
    }

    public SalesUploadReceipt upload(SalesUploadCommand command) {
        validateCommand(command);
        validateStoreOwner(command);
        byte[] content = command.content();
        String checksum = sha256(content);
        StoredSalesFile storedFile = fileStorage.store(new SalesFile(
                command.storeId(),
                command.originalFileName(),
                checksum,
                content
        ));

        SalesUploadReceipt pendingReceipt = lifecycleService.createPending(
                command.storeId(),
                command.requestedByUserId(),
                command.originalFileName(),
                storedFile.storageKey(),
                checksum
        );
        processingService.process(pendingReceipt.uploadId());
        return new SalesUploadReceipt(
                pendingReceipt.uploadId(),
                pendingReceipt.analysisRunId(),
                "COMPLETED"
        );
    }

    private void validateStoreOwner(SalesUploadCommand command) {
        if (!storeOwnershipRepository.existsActiveStoreOwnedBy(
                command.storeId(),
                command.requestedByUserId()
        )) {
            throw new SalesUploadRequestException(
                    "STORE_OWNER_REQUIRED",
                    "매장 소유자만 매출 파일을 업로드할 수 있습니다."
            );
        }
    }

    private void validateCommand(SalesUploadCommand command) {
        if (!command.originalFileName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new SalesUploadRequestException(
                    "UNSUPPORTED_FILE_FORMAT",
                    "지원하지 않는 파일 형식입니다."
            );
        }
        int size = command.content().length;
        if (size == 0) {
            throw new SalesUploadRequestException("EMPTY_FILE", "빈 파일은 업로드할 수 없습니다.");
        }
        if (size > MAX_FILE_SIZE_BYTES) {
            throw new SalesUploadRequestException(
                    "FILE_TOO_LARGE",
                    "파일 크기가 10MB를 초과했습니다."
            );
        }
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

}
