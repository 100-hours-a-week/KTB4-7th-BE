package com.memme.service.sales.storage;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class LocalSalesFileStorageTest {

    @TempDir
    Path storageDirectory;

    @Test
    void sameFileCanBeStoredWithDifferentObjectKeys() {
        LocalSalesFileStorage storage = new LocalSalesFileStorage(storageDirectory);
        byte[] content = "same-sales-file".getBytes();
        SalesFile file = new SalesFile(1L, "sales.xlsx", "a".repeat(64), content);

        StoredSalesFile first = storage.store(file);
        StoredSalesFile second = storage.store(file);

        assertThat(first.storageKey()).isNotEqualTo(second.storageKey());
        assertThat(storage.load(first.storageKey())).isEqualTo(content);
        assertThat(storage.load(second.storageKey())).isEqualTo(content);
    }
}
