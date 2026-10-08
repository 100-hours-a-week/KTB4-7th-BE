package com.memme.service.store;

import java.math.BigDecimal;
import java.util.List;

public interface MenuRecognitionAiClient {

    List<DetectedItem> recognize(long storeId, long batchId, List<Image> images);

    record Image(long imageId, String imageUrl, int order) {
    }

    record DetectedItem(long sourceImageId, String name, Long price, String category,
            BigDecimal confidence, boolean priceReviewRequired) {
    }
}
