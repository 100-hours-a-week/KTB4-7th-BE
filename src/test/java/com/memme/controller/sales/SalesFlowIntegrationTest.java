package com.memme.controller.sales;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.entity.sales.AnalysisRunStatus;
import com.memme.entity.sales.SalesAiInsightStatus;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.repository.sales.AnalysisRunRepository;
import com.memme.repository.sales.SalesAiInsightRepository;
import com.memme.repository.sales.SalesDailySummaryRepository;
import com.memme.repository.store.StoreOwnershipRepository;
import com.memme.service.sales.TossPosWorkbookParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class SalesFlowIntegrationTest {
    private static final long STORE_ID = 93001L;
    private static final long USER_ID = 93007L;
    private static final AuthenticatedUserSession USER = new AuthenticatedUserSession(USER_ID, STORE_ID);
    private static final Path STORAGE;
    static {
        try { STORAGE = Files.createTempDirectory("memme-sales-flow-"); }
        catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.sales.storage-directory", () -> STORAGE.toString());
    }
    @Autowired SalesUploadController uploadController;
    @Autowired SalesAnalysisController analysisController;
    @Autowired GlobalExceptionHandler globalHandler;
    @Autowired SalesUploadExceptionHandler uploadHandler;
    @Autowired TossPosWorkbookParser parser;
    @Autowired SalesDailySummaryRepository summaries;
    @Autowired AnalysisRunRepository runs;
    @Autowired SalesAiInsightRepository insights;
    @Autowired JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM stores WHERE id = ?", STORE_ID);
        jdbc.update("DELETE FROM users WHERE id = ?", USER_ID);
        jdbc.update("""
                INSERT INTO users(
                    id, email, password_hash, phone, created_at, updated_at
                ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, USER_ID, "sales-flow@example.com", "test-password-hash", "01000000000");
        jdbc.update("""
                INSERT INTO stores(
                    id, owner_user_id, business_registration_no, business_verified_at,
                    name, postal_code, address, status, created_at, updated_at
                ) VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, STORE_ID, USER_ID, "1234567890", "테스트 매장", "01234", "서울시 테스트구");
        mvc = MockMvcBuilders.standaloneSetup(uploadController, analysisController)
                .setControllerAdvice(globalHandler, uploadHandler).build();
    }

    @Test
    void syntheticWorkbookUploadAnalysisAndReuploadKeepTotalsStable() throws Exception {
        byte[] content;
        try (InputStream input = getClass().getResourceAsStream("/sales/toss-pos/sample-normal.xlsx")) {
            content = input.readAllBytes();
        }
        var parsed = parser.parse(new java.io.ByteArrayInputStream(content));
        long expectedSales = parsed.items().stream().mapToLong(item -> item.netAmount()).sum();
        long expectedOrders = parsed.orders().stream().filter(order -> order.valid()).count();
        for (int i = 0; i < 2; i++) {
            var response = mvc.perform(multipart("/v1/sales/uploads")
                            .file(new MockMultipartFile("file", "sample.xlsx", null, content))
                            .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, USER))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andReturn().getResponse().getContentAsString();
            var json = new tools.jackson.databind.ObjectMapper().readTree(response);
            long uploadId = json.path("data").path("uploadId").asLong();
            long runId = json.path("data").path("analysisRunId").asLong();
            assertThat(runs.findById(runId).orElseThrow().getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
            assertThat(insights.findByStoreIdAndTargetMonth(
                    STORE_ID,
                    YearMonth.from(parsed.periodEnd())
            ).orElseThrow().getStatus()).isEqualTo(SalesAiInsightStatus.INSUFFICIENT_DATA);
            mvc.perform(get("/v1/sales/uploads")
                            .param("targetMonth", parsed.periodStart().toString().substring(0, 7))
                            .param("page", "1")
                            .param("size", "10")
                            .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.connection.latestStatus").value("COMPLETED"))
                    .andExpect(jsonPath("$.items[0].uploadId").value(uploadId));
            mvc.perform(get("/v1/sales/uploads/{uploadId}", uploadId)
                            .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.analysisRunId").value(runId))
                    .andExpect(jsonPath("$.data.progress.percent").value(100));
            mvc.perform(get("/v1/sales/analyses").param("periodType", "CUSTOM")
                            .param("startDate", parsed.periodStart().toString())
                            .param("endDate", parsed.periodEnd().toString())
                            .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, USER)
                            .param("storeId", "999999").param("userId", "999999"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.kpis.totalSales").value(expectedSales))
                    .andExpect(jsonPath("$.data.kpis.orderCount").value(expectedOrders))
                    .andExpect(jsonPath("$.data.aiInsight").isEmpty());
        }
        // Invalid workbook must not replace previously successful sales.
        mvc.perform(multipart("/v1/sales/uploads")
                        .file(new MockMultipartFile("file", "invalid.xlsx", null, new byte[]{1, 2, 3}))
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, USER))
                .andExpect(status().is4xxClientError());
        assertThat(summaries.findAllByStoreIdAndSalesDateBetweenOrderBySalesDateAsc(
                STORE_ID, parsed.periodStart(), parsed.periodEnd()).stream()
                .mapToLong(summary -> summary.getTotalNetAmount()).sum()).isEqualTo(expectedSales);
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mvc.perform(get("/v1/sales/analyses")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
        mvc.perform(multipart("/v1/sales/uploads")
                        .file(new MockMultipartFile("file", "sample.xlsx", null, new byte[]{1})))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/sales/uploads"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/sales/uploads/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsOtherStoreAndInvalidDatesAndReturnsEmpty() throws Exception {
        mvc.perform(get("/v1/sales/analyses")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(USER_ID + 1, STORE_ID)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/sales/uploads")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE,
                                new AuthenticatedUserSession(USER_ID + 1, STORE_ID)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/sales/uploads")
                        .param("page", "6")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, USER))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v1/sales/analyses").param("periodType", "CUSTOM")
                        .param("startDate", "2026-02-30").param("endDate", "2026-03-01")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, USER))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/v1/sales/analyses").param("periodType", "CUSTOM")
                        .param("startDate", "1901-01-01").param("endDate", "1901-01-02")
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, USER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EMPTY"))
                .andExpect(jsonPath("$.data.dailySales").isEmpty());
    }
}
