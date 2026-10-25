package com.memme.entity.sales;

public record SalesUploadOutboxClaim(Long id, Long storeId, Long uploadId, Long analysisRunId, String token) {}
