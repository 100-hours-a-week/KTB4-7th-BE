package com.memme.dto.store;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record MenuConfirmationRequest(
        @NotNull Long expectedMenuRevision,
        @NotNull List<Item> items
) {
    public record Item(
            Long itemId,
            String name,
            Long price,
            String category,
            Integer order,
            Boolean priceConfirmed
    ) {
    }
}
