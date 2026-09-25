package com.memme.docker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalesAiInsightInitScriptTest {

    private static final Path SCRIPT = Path.of(
            "docker/mysql/init/04-sales-analysis-insights.sql"
    );

    @Test
    void createsSalesAnalysisAndMonthlyAiInsightTables() throws IOException {
        String sql = Files.readString(SCRIPT);

        assertThat(sql)
                .contains("CREATE TABLE sales_analyses")
                .contains("UNIQUE KEY uk_sales_analysis_run (analysis_run_id)")
                .contains("CREATE TABLE sales_ai_insights")
                .contains("insights JSON NULL")
                .contains("UNIQUE KEY uk_sales_ai_insights_store_month (store_id, target_month)")
                .contains("FOREIGN KEY (sales_analysis_id) REFERENCES sales_analyses(id)")
                .contains("'INSUFFICIENT_DATA'");
    }
}
