package com.memme.controller.sales;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.dto.sales.SalesUploadHistoryRequest;
import com.memme.dto.sales.SalesUploadHistoryResponse;
import com.memme.dto.sales.SalesUploadStatusResponse;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.service.sales.upload.SalesUploadQueryService;
import com.memme.service.sales.upload.SalesUploadReceipt;
import com.memme.service.sales.upload.SalesUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalesUploadControllerTest {

    private SalesUploadService salesUploadService;
    private SalesUploadQueryService salesUploadQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        salesUploadService = mock(SalesUploadService.class);
        salesUploadQueryService = mock(SalesUploadQueryService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SalesUploadController(
                        salesUploadService,
                        salesUploadQueryService
                ))
                .setControllerAdvice(
                        new SalesUploadExceptionHandler(),
                        new GlobalExceptionHandler()
                )
                .build();
    }

    @Test
    void returnsUploadHistory() throws Exception {
        YearMonth targetMonth = YearMonth.of(2026, 9);
        SalesUploadHistoryRequest request = new SalesUploadHistoryRequest(targetMonth, 1, 10);
        SalesUploadHistoryResponse response = new SalesUploadHistoryResponse(
                new SalesUploadHistoryResponse.Connection(
                        OffsetDateTime.parse("2026-09-21T12:30:00+09:00"),
                        18L,
                        "COMPLETED"
                ),
                List.of(new SalesUploadHistoryResponse.Item(
                        12L,
                        "sales.xlsx",
                        OffsetDateTime.parse("2026-09-21T12:30:00+09:00"),
                        20L,
                        18L,
                        "COMPLETED",
                        null
                )),
                1,
                10,
                1,
                1L
        );
        when(salesUploadQueryService.getHistory(7L, 301L, request)).thenReturn(response);

        mockMvc.perform(get("/v1/sales/uploads")
                        .param("targetMonth", "2026-09")
                        .param("page", "1")
                        .param("size", "10")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(7L, 301L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connection.latestStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].uploadId").value(12))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void returnsUploadStatus() throws Exception {
        SalesUploadStatusResponse response = new SalesUploadStatusResponse(
                "매출 파일을 처리하고 있습니다.",
                "PROCESSING",
                null,
                new SalesUploadStatusResponse.Data(
                        12L,
                        34L,
                        new SalesUploadStatusResponse.Progress("ANALYZING", 90),
                        null,
                        false
                )
        );
        when(salesUploadQueryService.getStatus(7L, 301L, 12L)).thenReturn(response);

        mockMvc.perform(get("/v1/sales/uploads/12")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(7L, 301L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.analysisRunId").value(34))
                .andExpect(jsonPath("$.data.progress.step").value("ANALYZING"))
                .andExpect(jsonPath("$.data.progress.percent").value(90));
    }

    @Test
    void returnsCompletedResult() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "sales.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        );
        when(salesUploadService.upload(any())).thenReturn(new SalesUploadReceipt(12L, 34L, "COMPLETED"));

        mockMvc.perform(multipart("/v1/sales/uploads")
                        .file(file)
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(7L, 301L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("매출 파일 업로드가 완료되었습니다."))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.uploadId").value(12))
                .andExpect(jsonPath("$.data.analysisRunId").value(34))
                .andExpect(jsonPath("$.data.status").doesNotExist());
    }

    @Test
    void rejectsMultipleFiles() throws Exception {
        MockMultipartFile first = new MockMultipartFile("file", "first.xlsx", null, new byte[]{1});
        MockMultipartFile second = new MockMultipartFile("file", "second.xlsx", null, new byte[]{2});

        mockMvc.perform(multipart("/v1/sales/uploads")
                        .file(first)
                        .file(second)
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(7L, 301L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.failReason").value("INVALID_FILE_COUNT"));
        verifyNoInteractions(salesUploadService);
    }
}
