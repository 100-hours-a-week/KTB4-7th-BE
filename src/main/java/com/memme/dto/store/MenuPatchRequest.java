package com.memme.dto.store;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

public record MenuPatchRequest(
        @NotNull Long expectedMenuRevision,
        @NotNull List<Item> items
) {
    public record Item(
            Long menuId,
            String name,
            @JsonDeserialize(using = StrictMenuPriceDeserializer.class) Long price,
            String category,
            Integer order,
            Boolean priceConfirmed
    ) {
    }
}
