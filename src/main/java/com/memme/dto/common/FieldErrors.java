package com.memme.dto.common;

import java.util.List;

public record FieldErrors(
        List<FieldError> fieldErrors
) {
}
