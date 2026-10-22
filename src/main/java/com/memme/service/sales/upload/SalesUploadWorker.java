package com.memme.service.sales.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SalesUploadWorker {

    private static final Logger log = LoggerFactory.getLogger(SalesUploadWorker.class);
    private final SalesUploadProcessingService processingService;

    public SalesUploadWorker(SalesUploadProcessingService processingService) {
        this.processingService = processingService;
    }

    @Async("salesUploadExecutor")
    public void process(Long uploadId) {
        try {
            processingService.process(uploadId);
        } catch (RuntimeException exception) {
            log.error("Sales upload processing failed for uploadId={}", uploadId, exception);
        }
    }
}
