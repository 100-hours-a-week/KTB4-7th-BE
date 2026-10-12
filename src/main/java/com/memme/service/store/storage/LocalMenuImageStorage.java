package com.memme.service.store.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.menu.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalMenuImageStorage implements MenuImageStorage {

    private final Path rootDirectory;

    public LocalMenuImageStorage(
            @Value("${app.menu.storage.local-directory:${java.io.tmpdir}/memme-menu-images}") Path rootDirectory) {
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
    }

    @Override
    public String store(MenuImageFile image) {
        String storageKey = MenuImageStorageKey.create(image);
        Path target = target(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, image.content(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return storageKey;
        } catch (IOException exception) {
            if (!(exception instanceof FileAlreadyExistsException)) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw new UncheckedIOException("메뉴 이미지를 저장하지 못했습니다.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(target(storageKey));
        } catch (IOException exception) {
            throw new UncheckedIOException("메뉴 이미지를 삭제하지 못했습니다.", exception);
        }
    }

    @Override
    public URI temporaryReadUrl(String storageKey) {
        MenuImageStorageKey.validate(storageKey);
        throw new IllegalStateException("로컬 저장소는 외부 AI용 이미지 URL을 제공하지 않습니다.");
    }

    private Path target(String storageKey) {
        Path target = rootDirectory.resolve(MenuImageStorageKey.validate(storageKey)).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("invalid menu image storage key");
        }
        return target;
    }
}
