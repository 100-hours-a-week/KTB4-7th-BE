package com.memme.service.noti.batch;

import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

public final class BatchMetadataTestSupport {

    private BatchMetadataTestSupport() {
    }

    public static void initializeSchemaIfNeeded(DataSource dataSource) throws Exception {
        if (batchInstanceTableExists(dataSource)) {
            return;
        }
        if (System.getenv("MEMME_TEST_MYSQL_URL") != null) {
            throw new IllegalStateException("Apply the Spring Batch metadata migration before running the MySQL test");
        }
        new ResourceDatabasePopulator(
                new ClassPathResource("org/springframework/batch/core/schema-h2.sql"))
                .execute(dataSource);
    }

    public static void clearExecutions(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT");
        jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT");
        jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION");
        jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_PARAMS");
        jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION");
        jdbcTemplate.update("DELETE FROM BATCH_JOB_INSTANCE");
    }

    private static boolean batchInstanceTableExists(DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection();
                var tables = connection.getMetaData().getTables(
                        connection.getCatalog(), null, "BATCH_JOB_INSTANCE", new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
