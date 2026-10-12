package com.memme.service.store.storage;

import java.util.UUID;
import java.util.regex.Pattern;

final class MenuImageStorageKey {

    private static final Pattern VALID_KEY = Pattern.compile(
            "menu-images/[1-9][0-9]*/[a-f0-9-]{36}\\.(jpg|png)");

    private MenuImageStorageKey() {
    }

    static String create(MenuImageFile image) {
        return "menu-images/" + image.storeId() + "/" + UUID.randomUUID() + "." + image.extension();
    }

    static String validate(String storageKey) {
        if (storageKey == null || !VALID_KEY.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("invalid menu image storage key");
        }
        return storageKey;
    }
}
