package com.memme.service.store.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalMenuImageStorageTest {

    @TempDir
    Path directory;

    @Test
    void 메뉴_이미지를_비공개_키로_저장하고_삭제한다() throws Exception {
        var storage = new LocalMenuImageStorage(directory);
        byte[] content = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9};

        String key = storage.store(new MenuImageFile(7, "image/jpeg", content));

        assertThat(key).matches("menu-images/7/[a-f0-9-]{36}\\.jpg");
        assertThat(Files.readAllBytes(directory.resolve(key))).isEqualTo(content);
        assertThatThrownBy(() -> storage.temporaryReadUrl(key))
                .isInstanceOf(IllegalStateException.class);

        storage.delete(key);
        assertThat(Files.exists(directory.resolve(key))).isFalse();
        storage.delete(key);
    }

    @Test
    void 저장_키로_상위_디렉터리에_접근할_수_없다() {
        var storage = new LocalMenuImageStorage(directory);

        assertThatThrownBy(() -> storage.delete("../outside.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 이미지_입력은_형식과_용량을_확인하고_바이트를_복사한다() {
        byte[] content = {1, 2};
        var image = new MenuImageFile(7, "image/png", content);
        content[0] = 9;

        assertThat(image.content()).containsExactly(1, 2);
        assertThatThrownBy(() -> new MenuImageFile(7, "image/gif", content))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MenuImageFile(7, "image/png", new byte[5_242_881]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
