package com.memme.service.store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.sales.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalMenuImageStorage implements MenuImageStorage {

    private final Path root;

    public LocalMenuImageStorage(@Value("${java.io.tmpdir}/memme-menu-images") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String store(long storeId, byte[] content, String mimeType) {
        String extension = "image/png".equals(mimeType) ? ".png" : ".jpg";
        String key = storeId + "/" + UUID.randomUUID() + extension;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return key;
        } catch (IOException exception) {
            throw new UncheckedIOException("메뉴 이미지를 저장하지 못했습니다.", exception);
        }
    }

    @Override
    public String signedReadUrl(String storageKey) {
        throw new IllegalStateException("로컬 저장소는 AI 서버용 서명 URL을 제공하지 않습니다.");
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            throw new UncheckedIOException("메뉴 이미지를 정리하지 못했습니다.", exception);
        }
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("잘못된 이미지 저장 키입니다.");
        }
        return target;
    }
}
