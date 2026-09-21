package com.memme.service.sales.upload;

import com.memme.entity.sales.AnalysisRunStatus;
import com.memme.entity.sales.SalesUploadStatus;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesUploadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SalesUploadLifecycleServiceTest {

    @Autowired private SalesUploadLifecycleService lifecycleService;
    @Autowired private SalesUploadRepository uploadRepository;
    @Autowired private AnalysisRunRepository analysisRunRepository;

    @Test
    void createsUploadAndAnalysisRunTogether() {
        SalesUploadReceipt receipt = lifecycleService.createPending(
                301L,
                7L,
                "sales.xlsx",
                "301/ab/file.xlsx",
                "a".repeat(64)
        );

        var upload = uploadRepository.findById(receipt.uploadId()).orElseThrow();
        var run = analysisRunRepository.findById(receipt.analysisRunId()).orElseThrow();
        assertThat(receipt.status()).isEqualTo("PENDING");
        assertThat(upload.getStatus()).isEqualTo(SalesUploadStatus.PENDING);
        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.PENDING);
        assertThat(run.getBasedOnUploadId()).isEqualTo(upload.getId());
        assertThat(run.getEngineVersion()).isEqualTo("sales-v1");
        assertThat(run.getIdempotencyKey()).isNotBlank();
    }
}
