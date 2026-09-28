CREATE TABLE analysis_metrics (
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

CREATE TABLE menu_analysis_results (
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
