package com.memme.service.store;

import com.memme.dto.store.MenuImageUploadResponse;
import com.memme.entity.store.MenuImage;
import com.memme.entity.store.MenuUploadBatch;
import com.memme.entity.store.Store;
import com.memme.exception.store.MenuRequestException;
import com.memme.repository.store.MenuImageRepository;
import com.memme.repository.store.MenuUploadBatchRepository;
import com.memme.repository.store.StoreRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MenuUploadService {

    private static final Logger log = LoggerFactory.getLogger(MenuUploadService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    private final StoreRepository storeRepository;
    private final MenuUploadBatchRepository batchRepository;
    private final MenuImageRepository imageRepository;
    private final MenuQueryService queryService;
    private final MenuImageStorage storage;
    private final ApplicationEventPublisher events;

    public MenuUploadService(StoreRepository storeRepository, MenuUploadBatchRepository batchRepository,
            MenuImageRepository imageRepository, MenuQueryService queryService,
            MenuImageStorage storage, ApplicationEventPublisher events) {
        this.storeRepository = storeRepository;
        this.batchRepository = batchRepository;
        this.imageRepository = imageRepository;
        this.queryService = queryService;
        this.storage = storage;
        this.events = events;
    }

    @Transactional
    public MenuImageUploadResponse upload(long userId, long storeId,
            String idempotencyKey, List<MultipartFile> images) {
        queryService.requireOwnedStore(userId, storeId);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new MenuRequestException(HttpStatus.BAD_REQUEST, "Idempotency-Key를 입력해 주세요.");
        }
        List<ValidatedImage> files = validateImages(images);
        String requestHash = requestHash(files);
        Store store = storeRepository.findForMenuUpdate(storeId)
                .orElseThrow(() -> new MenuRequestException(HttpStatus.NOT_FOUND, "매장 정보를 찾을 수 없습니다."));
        String keyHash = hash(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        MenuUploadBatch existing = batchRepository.findByStoreIdAndIdempotencyKeyHash(storeId, keyHash)
                .orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new MenuRequestException(HttpStatus.CONFLICT,
                        "같은 멱등 키로 다른 이미지를 업로드할 수 없습니다.");
            }
            return response(existing, images.size());
        }
        LocalDateTime now = LocalDateTime.now(SEOUL);
        batchRepository.findFirstByStoreIdOrderByUploadedAtDescIdDesc(storeId)
                .filter(batch -> batch.getSavedAt() == null && batch.getSupersededAt() == null)
                .ifPresent(batch -> batch.markSuperseded(now));
        MenuUploadBatch batch = batchRepository.save(MenuUploadBatch.pending(storeId,
                store.getMenuRevision(), keyHash, requestHash, now));
        List<String> storedKeys = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    cleanup(storedKeys);
                }
            }
        });
        try {
            for (int index = 0; index < files.size(); index++) {
                ValidatedImage file = files.get(index);
                String key = storage.store(storeId, file.content(), file.mimeType());
                storedKeys.add(key);
                imageRepository.save(MenuImage.pending(batch.getId(), storeId, index + 1,
                        key, file.content().length, file.mimeType(), now));
            }
        } catch (RuntimeException exception) {
            throw new MenuRequestException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지를 저장하지 못했습니다. 다시 시도해주세요.");
        }
        events.publishEvent(new MenuBatchAccepted(storeId, batch.getId()));
        return response(batch, files.size());
    }

    private List<ValidatedImage> validateImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw new MenuRequestException(HttpStatus.BAD_REQUEST, "업로드할 이미지를 선택해 주세요.");
        }
        if (images.size() > 10) {
            throw new MenuRequestException(HttpStatus.BAD_REQUEST, "이미지는 최대 10장까지 업로드할 수 있습니다.",
                    Map.of("code", "TOO_MANY_IMAGES"));
        }
        long total = 0;
        List<ValidatedImage> result = new ArrayList<>();
        for (int index = 0; index < images.size(); index++) {
            MultipartFile image = images.get(index);
            if (image == null || image.isEmpty()) {
                throw fileError(HttpStatus.BAD_REQUEST, "EMPTY_FILE", index, "빈 이미지 파일입니다.");
            }
            if (image.getSize() > MAX_FILE_BYTES) {
                throw fileError(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", index,
                        "이미지 파일은 장당 5MiB 이하여야 합니다.");
            }
            String name = image.getOriginalFilename();
            String extension = name == null ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase();
            String mime = image.getContentType();
            boolean jpeg = ("jpg".equals(extension) || "jpeg".equals(extension)) && "image/jpeg".equals(mime);
            boolean png = "png".equals(extension) && "image/png".equals(mime);
            if (!jpeg && !png) {
                throw fileError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE", index,
                        "jpg, jpeg, png 이미지만 업로드할 수 있습니다.");
            }
            byte[] content;
            try {
                content = image.getBytes();
            } catch (IOException exception) {
                throw fileError(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", index,
                        "이미지를 읽지 못했습니다. 다시 시도해주세요.");
            }
            if (content.length > MAX_FILE_BYTES) {
                throw fileError(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", index,
                        "이미지 파일은 장당 5MiB 이하여야 합니다.");
            }
            total += content.length;
            if (total > MAX_TOTAL_BYTES) {
                throw fileError(HttpStatus.CONTENT_TOO_LARGE, "TOTAL_TOO_LARGE", index,
                        "전체 이미지 용량은 50MiB 이하여야 합니다.");
            }
            if (!matchesSignature(content, jpeg)) {
                throw fileError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE", index,
                        "이미지 파일 형식을 확인해 주세요.");
            }
            result.add(new ValidatedImage(content, mime));
        }
        return List.copyOf(result);
    }

    private boolean matchesSignature(byte[] content, boolean jpeg) {
        if (jpeg) {
            return content.length >= 4 && (content[0] & 0xff) == 0xff
                    && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff;
        }
        if (content.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (content[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private String requestHash(List<ValidatedImage> images) {
        StringBuilder value = new StringBuilder();
        for (ValidatedImage image : images) {
            value.append(image.mimeType()).append(':').append(image.content().length)
                    .append(':').append(hash(image.content())).append(';');
        }
        return hash(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String hash(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private MenuImageUploadResponse response(MenuUploadBatch batch, int imageCount) {
        return new MenuImageUploadResponse(batch.getId(), imageCount,
                MenuQueryService.atSeoul(batch.getUploadedAt()), "MENU_LIST");
    }

    private MenuRequestException fileError(HttpStatus status, String code, int fileIndex, String message) {
        return new MenuRequestException(status, message, Map.of("code", code, "fileIndex", fileIndex));
    }

    private void cleanup(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException exception) {
                log.warn("메뉴 이미지 롤백 정리 실패");
            }
        }
    }

    private record ValidatedImage(byte[] content, String mimeType) {
    }
}
