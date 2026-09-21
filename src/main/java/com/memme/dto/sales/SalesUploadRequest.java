package com.memme.dto.sales;

import org.springframework.web.multipart.MultipartFile;

public record SalesUploadRequest(
        MultipartFile file
) {}
