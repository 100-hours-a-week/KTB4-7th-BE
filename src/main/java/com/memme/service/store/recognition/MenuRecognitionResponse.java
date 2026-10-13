package com.memme.service.store.recognition;

import java.math.BigDecimal;
import java.util.List;

public record MenuRecognitionResponse(String message, Data data) {

    public record Data(List<DetectedItem> detectedItems, List<FailedImage> failedImages) {
        public Data {
            failedImages = failedImages == null ? List.of() : List.copyOf(failedImages);
        }

        public Data(List<DetectedItem> detectedItems) {
            this(detectedItems, List.of());
        }
    }

    public record FailedImage(Long sourceImageId, String failReason) {
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
