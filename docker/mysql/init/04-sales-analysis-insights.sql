CREATE TABLE sales_analyses (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  analysis_run_id BIGINT UNSIGNED NOT NULL,
  summary_text TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sales_analysis_run (analysis_run_id)
) ENGINE=InnoDB;

CREATE TABLE sales_ai_insights (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT UNSIGNED NOT NULL,
  sales_analysis_id BIGINT UNSIGNED NOT NULL,
  target_month DATE NOT NULL,
  insights JSON NULL,
  status VARCHAR(30) NOT NULL,
  generated_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sales_ai_insights_store_month (store_id, target_month),
  KEY idx_sales_ai_insights_analysis_id (sales_analysis_id),
  CONSTRAINT fk_sales_ai_insights_analysis
    FOREIGN KEY (sales_analysis_id) REFERENCES sales_analyses(id),
  CONSTRAINT chk_sales_ai_insights_target_month
    CHECK (DAY(target_month) = 1),
  CONSTRAINT chk_sales_ai_insights_status
    CHECK (status IN ('PENDING', 'GENERATING', 'COMPLETED', 'INSUFFICIENT_DATA', 'FAILED'))
) ENGINE=InnoDB;
