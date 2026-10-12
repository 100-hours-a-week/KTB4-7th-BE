package com.memme.service.store.storage;

import java.net.URI;

public interface MenuImageStorage {

    String store(MenuImageFile image);

    void delete(String storageKey);

    URI temporaryReadUrl(String storageKey);
}
