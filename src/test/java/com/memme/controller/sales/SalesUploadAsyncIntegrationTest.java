package com.memme.controller.sales;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.exception.sales.SalesUploadExceptionHandler;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesUploadRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.service.sales.upload.SalesUploadProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class SalesUploadAsyncIntegrationTest {

    @Autowired SalesUploadController controller;
    @Autowired GlobalExceptionHandler globalHandler;
    @Autowired SalesUploadExceptionHandler uploadHandler;
    @Autowired SalesUploadRepository uploads;
    @Autowired AnalysisRunRepository runs;
    @Autowired ApplicationContext context;
    @MockitoBean SalesUploadProcessingService processingService;
    @MockitoBean StoreOwnershipRepository ownership;

    @Test
    void returnsPendingWhileWorkerIsBlockedAndWorkerCanReadCommittedReceipt() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> workerThread = new AtomicReference<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        when(ownership.existsActiveStoreOwnedBy(96001L, 96007L)).thenReturn(true);
        doAnswer(invocation -> {
            try {
                Long id = invocation.getArgument(0);
                assertThat(uploads.findById(id)).isPresent();
                assertThat(runs.findFirstByBasedOnUploadIdOrderByIdDesc(id)).isPresent();
                workerThread.set(Thread.currentThread().getName());
                started.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (Throwable failure) {
                workerFailure.set(failure);
            } finally {
                finished.countDown();
            }
            return null;
        }).when(processingService).process(anyLong());
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(globalHandler, uploadHandler).build();
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    mvc.perform(multipart("/v1/sales/uploads")
                                    .file(new MockMultipartFile("file", "sales.xlsx", null, new byte[]{1}))
                                    .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                            new AuthenticatedUserSession(96007L, 96001L)))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.status").value("PENDING"))
                            .andExpect(jsonPath("$.data.uploadId").isNumber())
                            .andExpect(jsonPath("$.data.analysisRunId").isNumber()));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(finished.getCount()).isEqualTo(1);
            assertThat(workerThread.get()).startsWith("sales-upload-");
            assertThat(context.getBean("applicationTaskExecutor"))
                    .isNotSameAs(context.getBean("salesUploadExecutor"));
        } finally {
            release.countDown();
            assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(workerFailure.get()).isNull();
    }
}
