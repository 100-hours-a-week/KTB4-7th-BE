package com.memme.service.store.upload;

import com.memme.dto.store.MenuImageUploadResponse;

public record MenuUploadResult(String status, MenuImageUploadResponse data) {
}
