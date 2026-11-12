package com.memme.service.sales.upload;

import com.memme.repository.sales.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "OUTBOX_MYSQL_TEST_URL", matches = ".+/outbox_test.*")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DirtiesContext
class SalesUploadBatchMySqlIntegrationTest extends SalesUploadPersistenceServiceTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("OUTBOX_MYSQL_TEST_URL");
        if (url == null || !url.startsWith("jdbc:mysql://127.0.0.1:") || !url.contains("/outbox_test?")) {
            throw new IllegalArgumentException("isolated loopback outbox_test DB required");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
    }

    @Autowired
    SalesUploadBatchMySqlIntegrationTest(SalesUploadPersistenceService persistence, SalesUploadRepository uploads,
            SalesOrderRepository orders, SalesOrderItemRepository items, SalesDailySummaryRepository summaries) {
        super(persistence, uploads, orders, items, summaries);
    }
}
