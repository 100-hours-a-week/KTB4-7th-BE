-- Purpose: add the analysis tables missing from existing QA/production databases.
--
-- Run this script with a DB account that can create tables. It is intentionally
-- non-destructive: it does not delete or rewrite existing analysis records.
--
-- Do not add the sales_analyses -> analysis_runs foreign key in an existing
-- database until the preflight query at the end confirms there are no legacy
-- orphan analysis rows. Fresh databases receive that FK through init SQL.

CREATE TABLE IF NOT EXISTS analysis_runs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT UNSIGNED NOT NULL,
  requested_by_user_id BIGINT UNSIGNED NOT NULL,
  based_on_upload_id BIGINT UNSIGNED NOT NULL,
  analysis_type VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  period_start DATE NULL,
  period_end DATE NULL,
  engine_version VARCHAR(50) NOT NULL,
  idempotency_key VARCHAR(100) NOT NULL,
  error_message VARCHAR(500) NULL,
  started_at DATETIME NULL,
  completed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_analysis_runs_idempotency_key (idempotency_key),
  KEY idx_analysis_runs_store_completed (store_id, completed_at),
  CONSTRAINT fk_analysis_runs_store FOREIGN KEY (store_id) REFERENCES stores(id),
  CONSTRAINT fk_analysis_runs_user FOREIGN KEY (requested_by_user_id) REFERENCES users(id),
  CONSTRAINT fk_analysis_runs_upload FOREIGN KEY (based_on_upload_id) REFERENCES sales_uploads(id)
) ENGINE=InnoDB;

-- Keep IDs allocated for new runs distinct from analysis_run_id values that
-- may already exist in legacy sales_analyses rows.
SET @next_analysis_run_id := (
  SELECT GREATEST(
    COALESCE((SELECT MAX(id) FROM analysis_runs), 0),
    COALESCE((SELECT MAX(analysis_run_id) FROM sales_analyses), 0)
  ) + 1
);
SET @set_analysis_runs_auto_increment := CONCAT(
  'ALTER TABLE analysis_runs AUTO_INCREMENT = ', @next_analysis_run_id
);
PREPARE set_analysis_runs_auto_increment FROM @set_analysis_runs_auto_increment;
EXECUTE set_analysis_runs_auto_increment;
DEALLOCATE PREPARE set_analysis_runs_auto_increment;

CREATE TABLE IF NOT EXISTS analysis_metrics (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  sales_analysis_id BIGINT UNSIGNED NOT NULL,
  metric_code VARCHAR(50) NOT NULL,
  metric_value DECIMAL(18, 4) NOT NULL,
  comparison_value DECIMAL(18, 4) NULL,
  unit VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_analysis_metric_code (sales_analysis_id, metric_code),
  KEY idx_analysis_metrics_code (metric_code),
  CONSTRAINT fk_analysis_metrics_sales_analysis
    FOREIGN KEY (sales_analysis_id) REFERENCES sales_analyses(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS menu_analysis_results (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  sales_analysis_id BIGINT UNSIGNED NOT NULL,
  menu_id BIGINT UNSIGNED NULL,
  menu_name_snapshot VARCHAR(100) NOT NULL,
  sales_amount BIGINT NOT NULL,
  sales_quantity INT NOT NULL,
  sales_rank INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_menu_analysis_menu (sales_analysis_id, menu_id),
  CONSTRAINT fk_menu_analysis_results_sales_analysis
    FOREIGN KEY (sales_analysis_id) REFERENCES sales_analyses(id)
) ENGINE=InnoDB;

-- Post-apply checks: share the result with BE before adding the deferred FK.
SELECT
  (SELECT COUNT(*) FROM analysis_runs) AS analysis_run_count,
  (SELECT COUNT(*) FROM analysis_metrics) AS analysis_metric_count,
  (SELECT COUNT(*) FROM menu_analysis_results) AS menu_analysis_result_count;

SELECT sa.id, sa.analysis_run_id
FROM sales_analyses sa
LEFT JOIN analysis_runs ar ON ar.id = sa.analysis_run_id
WHERE ar.id IS NULL;
