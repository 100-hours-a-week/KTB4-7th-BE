package com.memme.dto.store;

import java.util.List;

public record MenuPatchResponse(
        long menuRevision,
        List<Item> items
) {
    public record Item(
            long id,
            int order,
            String name,
            long price,
            String category
    ) {
    }
}
