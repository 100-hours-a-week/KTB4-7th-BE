package com.memme.controller.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.memme.controller.auth.AuthenticatedUserSession;
import com.memme.exception.GlobalExceptionHandler;
import com.memme.exception.sales.SalesUploadExceptionHandler;
import com.memme.repository.store.StoreCostItemRepository;
import com.memme.service.sales.TossPosWorkbookParser;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:memme_profit_flow;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class ProfitFlowIntegrationTest {

    private static final long STORE_ID = 93901L;
    private static final long USER_ID = 93907L;
    private static final AuthenticatedUserSession USER = new AuthenticatedUserSession(USER_ID, STORE_ID);
    private static final Path STORAGE;

    static {
        try {
            STORAGE = Files.createTempDirectory("memme-profit-flow-");
        } catch (java.io.IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.sales.storage-directory", () -> STORAGE.toString());
    }

    @Autowired private SalesUploadController uploadController;
    @Autowired private ProfitAnalysisController profitController;
    @Autowired private GlobalExceptionHandler globalHandler;
    @Autowired private SalesUploadExceptionHandler uploadHandler;
    @Autowired private TossPosWorkbookParser parser;
    @Autowired private StoreCostItemRepository costItems;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void 매출_업로드가_비용을_매출_월로_복사하고_순이익을_조회한다() throws Exception {
        jdbc.update("""
                INSERT INTO users(id, email, password_hash, phone, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, USER_ID, "profit-flow@example.com", "test-password-hash", "01093900000");
        jdbc.update("""
                INSERT INTO stores(id, owner_user_id, business_registration_no, business_verified_at,
                                   name, postal_code, address, status, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, STORE_ID, USER_ID, "9390000000", "순이익 테스트 매장", "01234", "서울시 테스트구");
        byte[] content;
        try (InputStream input = getClass().getResourceAsStream("/sales/toss-pos/sample-normal.xlsx")) {
            content = input.readAllBytes();
        }
        var parsed = parser.parse(new java.io.ByteArrayInputStream(content));
        YearMonth salesMonth = YearMonth.from(parsed.periodStart());
        jdbc.update("""
                INSERT INTO store_cost_items(store_id, cost_month, rent_amount, labor_amount,
                                             ingredient_cost_rate, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, STORE_ID, salesMonth.minusMonths(1).atDay(1), 310_000, 620_000, new BigDecimal("0.4"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(uploadController, profitController)
                .setControllerAdvice(globalHandler, uploadHandler).build();

        mvc.perform(multipart("/v1/sales/uploads")
                        .file(new MockMultipartFile("file", "sample.xlsx", null, content))
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        for (YearMonth month : parsed.orders().stream()
                .map(order -> YearMonth.from(order.key().orderedAt())).distinct().toList()) {
            assertThat(costItems.findByStoreIdAndCostMonth(STORE_ID, month.atDay(1)))
                    .get().extracting(item -> item.getRentAmount())
                    .isEqualTo(310_000L);
        }
        String response = mvc.perform(get("/v2/sales/profit-analyses")
                        .param("periodType", "CUSTOM")
                        .param("startDate", parsed.periodStart().toString())
                        .param("endDate", parsed.periodEnd().toString())
                        .sessionAttr(AuthenticatedUserSession.SESSION_ATTRIBUTE, USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.aiInsight.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        var json = new tools.jackson.databind.ObjectMapper().readTree(response);
        long dailyTotal = 0;
        for (var day : json.path("data").path("dailyProfits")) {
            dailyTotal += day.path("netProfit").asLong();
        }
        assertThat(dailyTotal).isEqualTo(json.path("data").path("summary").path("netProfit").asLong());
    }
}
