package com.memme.service.store.upload;

import com.memme.dto.store.MenuImageUploadResponse;
import com.memme.entity.store.MenuProcessingStatus;
import com.memme.repository.store.MenuUploadBatchRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.repository.store.StoreRepository;
import com.memme.service.store.recognition.MenuRecognitionAiClient;
import com.memme.service.store.recognition.MenuRecognitionAiException;
import com.memme.service.store.recognition.MenuRecognitionRequest;
import com.memme.service.store.recognition.MenuRecognitionResponse;
import com.memme.service.store.storage.MenuImageFile;
import com.memme.service.store.storage.MenuImageStorage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MenuUploadService {

    private static final int MAX_IMAGES = 10;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    private static final long REPLAY_WAIT_MILLIS = 130_000;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final StoreRepository stores;
    private final StoreOwnershipRepository ownership;
    private final MenuUploadBatchRepository batches;
    private final MenuImageStorage storage;
    private final MenuRecognitionAiClient ai;
    private final MenuUploadLifecycleService lifecycle;

    public MenuUploadService(StoreRepository stores, StoreOwnershipRepository ownership,
            MenuUploadBatchRepository batches, MenuImageStorage storage,
            MenuRecognitionAiClient ai, MenuUploadLifecycleService lifecycle) {
        this.stores = stores;
        this.ownership = ownership;
        this.batches = batches;
        this.storage = storage;
        this.ai = ai;
        this.lifecycle = lifecycle;
    }

    public MenuUploadResult upload(Long userId, String idempotencyKey, List<MenuUploadFile> files) {
        if (userId == null) {
            throw error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", null, "로그인이 필요합니다.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", null,
                    "Idempotency-Key가 필요합니다.");
        }
        List<MenuUploadFile> validFiles = validate(files);
        Long storeId = stores.findByOwnerId(userId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", null,
                        "매장을 찾을 수 없습니다.")).getId();
        if (!ownership.existsActiveStoreOwnedBy(storeId, userId)) {
            throw error(HttpStatus.FORBIDDEN, "STORE_FORBIDDEN", null,
                    "활성 매장의 소유자만 업로드할 수 있습니다.");
        }
        String keyHash = hex(sha256().digest(idempotencyKey.getBytes(StandardCharsets.UTF_8)));
        String requestHash = requestHash(validFiles);
        var existing = batches.findByStoreIdAndIdempotencyKeyHash(storeId, keyHash);
        if (existing.isPresent()) {
            return replay(storeId, existing.get().getId(), existing.get().getRequestHash(), requestHash);
        }

        List<StoredMenuUploadFile> stored = new ArrayList<>();
        MenuUploadLifecycleService.Creation creation;
        try {
            for (int index = 0; index < validFiles.size(); index++) {
                MenuUploadFile file = validFiles.get(index);
                String key = storage.store(new MenuImageFile(storeId, file.mimeType(), file.content()));
                stored.add(new StoredMenuUploadFile(index + 1, key, file.content().length, file.mimeType()));
            }
            creation = lifecycle.create(storeId, keyHash, requestHash, stored);
        } catch (RuntimeException exception) {
            cleanup(stored, exception);
            throw exception;
        }
        if (!creation.created()) {
            cleanup(stored, null);
            return replay(storeId, creation.batchId(), creation.requestHash(), requestHash);
        }

        List<MenuUploadLifecycleService.MenuImageReference> references = lifecycle.start(storeId, creation.batchId());
        MenuRecognitionResponse response;
        try {
            List<MenuRecognitionRequest.Image> requestedImages = new ArrayList<>();
            for (var image : references) {
                requestedImages.add(new MenuRecognitionRequest.Image(image.imageId(),
                        storage.temporaryReadUrl(image.storageKey()).toString(), image.order()));
            }
            response = ai.recognize(new MenuRecognitionRequest(storeId, creation.batchId(), requestedImages));
        } catch (MenuRecognitionAiException exception) {
            lifecycle.fail(storeId, creation.batchId(), exception.getFailReason());
            return result(lifecycle.snapshot(storeId, creation.batchId()));
        } catch (RuntimeException exception) {
            lifecycle.fail(storeId, creation.batchId(), "AI_PROCESSING_ERROR");
            return result(lifecycle.snapshot(storeId, creation.batchId()));
        }
        lifecycle.complete(storeId, creation.batchId(), response);
        return result(lifecycle.snapshot(storeId, creation.batchId()));
    }

    private MenuUploadResult replay(Long storeId, Long batchId, String originalHash, String requestHash) {
        if (!originalHash.equals(requestHash)) {
            throw error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", null,
                    "같은 Idempotency-Key로 다른 이미지를 업로드할 수 없습니다.");
        }
        long deadline = System.nanoTime() + REPLAY_WAIT_MILLIS * 1_000_000;
        while (true) {
            var snapshot = lifecycle.snapshot(storeId, batchId);
            if (snapshot.status() == MenuProcessingStatus.COMPLETED
                    || snapshot.status() == MenuProcessingStatus.FAILED) {
                return result(snapshot);
            }
            if (System.nanoTime() >= deadline) {
                throw error(HttpStatus.CONFLICT, "UPLOAD_IN_PROGRESS", null,
                        "동일한 업로드가 처리 중입니다. 잠시 후 다시 요청해주세요.");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw error(HttpStatus.SERVICE_UNAVAILABLE, "UPLOAD_INTERRUPTED", null,
                        "업로드 대기가 중단되었습니다.");
            }
        }
    }

    private MenuUploadResult result(MenuUploadLifecycleService.Snapshot snapshot) {
        OffsetDateTime uploadedAt = snapshot.uploadedAt().atZone(SEOUL).toOffsetDateTime();
        return new MenuUploadResult(snapshot.status().name(), new MenuImageUploadResponse(
                snapshot.batchId(), snapshot.imageCount(), uploadedAt, snapshot.failReason(), "MENU_LIST"));
    }

    private List<MenuUploadFile> validate(List<MenuUploadFile> files) {
        if (files == null || files.isEmpty() || files.size() > MAX_IMAGES) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_COUNT", null,
                    "메뉴 이미지는 1장부터 10장까지 업로드할 수 있습니다.");
        }
        long total = 0;
        for (int index = 0; index < files.size(); index++) {
            MenuUploadFile file = files.get(index);
            if (file == null || file.fileName() == null || file.mimeType() == null) {
                throw error(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", index, "이미지가 올바르지 않습니다.");
            }
            byte[] bytes = file.content();
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                throw error(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", index,
                        "각 이미지는 5MiB 이하여야 합니다.");
            }
            total += bytes.length;
            if (total > MAX_TOTAL_BYTES) {
                throw error(HttpStatus.PAYLOAD_TOO_LARGE, "TOTAL_IMAGES_TOO_LARGE", index,
                        "이미지 전체 크기는 50MiB 이하여야 합니다.");
            }
            String name = file.fileName().toLowerCase(Locale.ROOT);
            boolean jpeg = "image/jpeg".equals(file.mimeType())
                    && (name.endsWith(".jpg") || name.endsWith(".jpeg"))
                    && bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            boolean png = "image/png".equals(file.mimeType()) && name.endsWith(".png")
                    && bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                    && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
            if (!jpeg && !png) {
                throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE", index,
                        "JPG 또는 PNG 이미지만 업로드할 수 있습니다.");
            }
        }
        return List.copyOf(files);
    }

    private String requestHash(List<MenuUploadFile> files) {
        MessageDigest digest = sha256();
        for (MenuUploadFile file : files) {
            byte[] mime = file.mimeType().getBytes(StandardCharsets.UTF_8);
            byte[] content = file.content();
            digest.update(ByteBuffer.allocate(4).putInt(mime.length).array());
            digest.update(mime);
            digest.update(ByteBuffer.allocate(4).putInt(content.length).array());
            digest.update(content);
        }
        return hex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private void cleanup(List<StoredMenuUploadFile> files, RuntimeException original) {
        for (StoredMenuUploadFile file : files) {
            try {
                storage.delete(file.storageKey());
            } catch (RuntimeException exception) {
                if (original != null) {
                    original.addSuppressed(exception);
                }
            }
        }
    }

    private MenuUploadException error(HttpStatus status, String code, Integer index, String message) {
        return new MenuUploadException(status, code, index, message);
    }
}
