package com.memme.service.sales.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalSalesFileStorage implements SalesFileStorage {

    private final Path rootDirectory;

    public LocalSalesFileStorage(
            @Value("${app.sales.storage-directory:${java.io.tmpdir}/memme-sales}") Path rootDirectory
    ) {
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
    }

    @Override
    public StoredSalesFile store(SalesFile file) {
        String storageKey = file.storeId() + "/" + file.checksum().substring(0, 2)
                + "/" + file.checksum() + ".xlsx";
        Path target = rootDirectory.resolve(storageKey).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("invalid storage key");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(
                    target,
                    file.content(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            return new StoredSalesFile(storageKey);
        } catch (IOException exception) {
            throw new UncheckedIOException("매출 파일을 저장하지 못했습니다.", exception);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        Path target = rootDirectory.resolve(storageKey).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("invalid storage key");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException exception) {
            throw new UncheckedIOException("매출 파일을 읽지 못했습니다.", exception);
        }
    }
}
