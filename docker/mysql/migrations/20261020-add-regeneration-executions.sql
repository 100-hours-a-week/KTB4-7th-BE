CREATE TABLE sales_ai_insight_regenerations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT NOT NULL,
  target_month DATE NOT NULL,
  source_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(20) NOT NULL,
  attempt_count INT UNSIGNED NOT NULL,
  started_at DATETIME NOT NULL,
  completed_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sales_ai_insight_regen_store_month_source (store_id, target_month, source_fingerprint),
  CONSTRAINT fk_sales_ai_insight_regen_store FOREIGN KEY (store_id) REFERENCES stores(id),
  CONSTRAINT ck_sales_ai_insight_regen_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
  CONSTRAINT ck_sales_ai_insight_regen_attempt CHECK (attempt_count >= 1),
  CONSTRAINT ck_sales_ai_insight_regen_month CHECK (DAYOFMONTH(target_month) = 1),
  CONSTRAINT ck_sales_ai_insight_regen_completed CHECK (
    (status = 'COMPLETED' AND completed_at IS NOT NULL)
    OR (status <> 'COMPLETED' AND completed_at IS NULL)
  )
) ENGINE=InnoDB;

CREATE TABLE solution_regenerations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT NOT NULL,
  target_date DATE NOT NULL,
  source_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  status VARCHAR(20) NOT NULL,
  attempt_count INT UNSIGNED NOT NULL,
  started_at DATETIME NOT NULL,
  completed_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_solution_regen_store_date_source (store_id, target_date, source_fingerprint),
  CONSTRAINT fk_solution_regen_store FOREIGN KEY (store_id) REFERENCES stores(id),
  CONSTRAINT ck_solution_regen_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
  CONSTRAINT ck_solution_regen_attempt CHECK (attempt_count >= 1),
  CONSTRAINT ck_solution_regen_completed CHECK (
    (status = 'COMPLETED' AND completed_at IS NOT NULL)
    OR (status <> 'COMPLETED' AND completed_at IS NULL)
  )
) ENGINE=InnoDB;
