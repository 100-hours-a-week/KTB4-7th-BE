package com.memme.service.store.recognition;

import java.math.BigDecimal;
import java.util.List;

public record MenuRecognitionResponse(String message, Data data) {

    public record Data(List<DetectedItem> detectedItems) {
    }

    public record DetectedItem(
            Long sourceImageId,
            String name,
            Long price,
            String category,
            BigDecimal confidence,
            boolean priceReviewRequired
    ) {
    }
}
