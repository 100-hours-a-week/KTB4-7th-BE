package com.memme.service.store.recognition;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public record MenuRecognitionRequest(long storeId, long batchId, List<Image> images) {

    public MenuRecognitionRequest {
        if (storeId <= 0 || batchId <= 0 || images == null || images.isEmpty() || images.size() > 10) {
            throw new IllegalArgumentException("메뉴 인식 요청 대상이 올바르지 않습니다.");
        }
        images = List.copyOf(images);
    }

    public record Image(long imageId, String imageUrl, int order) {

        public Image {
            if (imageId <= 0 || order < 1 || order > 10
                    || !URI.create(Objects.requireNonNull(imageUrl, "imageUrl")).isAbsolute()) {
                throw new IllegalArgumentException("메뉴 인식 이미지 정보가 올바르지 않습니다.");
            }
        }
    }
}
