package com.memme.dto.common;

import java.util.List;

public record InsufficientDataResponse(
        List<String> missingData
) {}
