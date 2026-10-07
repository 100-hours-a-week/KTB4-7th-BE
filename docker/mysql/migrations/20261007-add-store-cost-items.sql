CREATE TABLE store_cost_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT UNSIGNED NOT NULL,
  cost_month DATE NOT NULL,
  rent_amount BIGINT UNSIGNED NOT NULL DEFAULT 0,
  labor_amount BIGINT UNSIGNED NOT NULL DEFAULT 0,
  ingredient_cost_rate DECIMAL(5,4) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cost_store_month (store_id, cost_month),
  CONSTRAINT fk_cost_store FOREIGN KEY (store_id) REFERENCES stores(id),
  CONSTRAINT ck_ingredient_cost_rate CHECK (ingredient_cost_rate BETWEEN 0 AND 1)
) ENGINE=InnoDB;
