CREATE TABLE sales_forecasts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT UNSIGNED NOT NULL,
  based_on_upload_id BIGINT UNSIGNED NOT NULL,
  target_date DATE NOT NULL,
  basis_date DATE NOT NULL,
  predicted_sales_amount BIGINT UNSIGNED NOT NULL,
  lower_bound BIGINT UNSIGNED NOT NULL,
  upper_bound BIGINT UNSIGNED NOT NULL,
  model_version VARCHAR(50) NOT NULL,
  generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sales_forecasts_store_target_date (store_id, target_date),
  CONSTRAINT chk_sales_forecasts_amounts
    CHECK (lower_bound <= predicted_sales_amount AND predicted_sales_amount <= upper_bound)
) ENGINE=InnoDB;
