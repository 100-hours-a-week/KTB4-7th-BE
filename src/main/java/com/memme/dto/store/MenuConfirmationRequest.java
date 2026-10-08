package com.memme.dto.store;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public record MenuConfirmationRequest(
        @NotNull Long expectedMenuRevision,
        @NotNull List<Item> items
) {
    public record Item(
            Long itemId,
            String name,
            @JsonDeserialize(using = StrictMenuPriceDeserializer.class) Long price,
            String category,
            Integer order,
            Boolean priceConfirmed
    ) {
    }
}
