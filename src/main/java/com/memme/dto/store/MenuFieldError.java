package com.memme.dto.store;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MenuFieldError(
        Long menuId,
        Long itemId,
        String field,
        String code,
        String message
) {
}
