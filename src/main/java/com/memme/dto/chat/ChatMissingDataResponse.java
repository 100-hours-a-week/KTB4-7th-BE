package com.memme.dto.chat;

import java.util.List;

public record ChatMissingDataResponse(
        List<String> missingData
) {
}
