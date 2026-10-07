CREATE TABLE menus (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL,
  normalized_name VARCHAR(100) NOT NULL,
  category VARCHAR(50) NOT NULL,
  price BIGINT UNSIGNED NOT NULL,
  sort_order INT UNSIGNED NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  removed_at DATETIME NULL,
  active_normalized_name VARCHAR(100) GENERATED ALWAYS AS
    (CASE WHEN removed_at IS NULL THEN normalized_name ELSE NULL END) STORED,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_menus_active_tuple (store_id, active_normalized_name, price, category),
  KEY idx_menus_store_current_order (store_id, removed_at, sort_order, id),
  CONSTRAINT fk_menu_store FOREIGN KEY (store_id) REFERENCES stores(id),
  CONSTRAINT ck_menu_price CHECK (price <= 1000000),
  CONSTRAINT ck_menu_status CHECK (status IN ('ACTIVE','INACTIVE','SOLD_OUT')),
  CONSTRAINT ck_menu_category CHECK (category IN ('COFFEE','NON_COFFEE','BEVERAGE','CAKE','BAKERY','OTHER'))
) ENGINE=InnoDB;

CREATE TABLE menu_upload_batches (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  fail_reason VARCHAR(50) NULL,
  base_menu_revision BIGINT UNSIGNED NOT NULL,
  idempotency_key_hash CHAR(64) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NULL,
  saved_at DATETIME NULL,
  superseded_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_menu_batch_store_idempotency (store_id, idempotency_key_hash),
  UNIQUE KEY uk_menu_batch_id_store (id, store_id),
  KEY idx_menu_batch_latest (store_id, uploaded_at, id),
  CONSTRAINT fk_menu_batch_store FOREIGN KEY (store_id) REFERENCES stores(id),
  CONSTRAINT ck_menu_batch_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED'))
) ENGINE=InnoDB;

CREATE TABLE menu_write_requests (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT UNSIGNED NOT NULL,
  operation VARCHAR(20) NOT NULL,
  key_hash CHAR(64) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  response_json JSON NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_menu_write_store_operation_key (store_id, operation, key_hash),
  KEY idx_menu_write_created (created_at),
  CONSTRAINT fk_menu_write_store FOREIGN KEY (store_id) REFERENCES stores(id),
  CONSTRAINT ck_menu_write_operation CHECK (operation IN ('MENU_PATCH','MENU_CONFIRM'))
) ENGINE=InnoDB;

CREATE TABLE menu_images (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  batch_id BIGINT UNSIGNED NOT NULL,
  store_id BIGINT UNSIGNED NOT NULL,
  image_order TINYINT UNSIGNED NOT NULL,
  storage_key VARCHAR(512) NOT NULL,
  file_size_bytes BIGINT UNSIGNED NOT NULL,
  mime_type VARCHAR(30) NOT NULL,
  ocr_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  ocr_result JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_menu_image_batch_order (batch_id, image_order),
  UNIQUE KEY uk_menu_image_storage_key (storage_key),
  CONSTRAINT fk_menu_image_batch_store FOREIGN KEY (batch_id, store_id)
    REFERENCES menu_upload_batches(id, store_id),
  CONSTRAINT ck_menu_image_order CHECK (image_order BETWEEN 1 AND 10),
  CONSTRAINT ck_menu_image_size CHECK (file_size_bytes <= 5242880),
  CONSTRAINT ck_menu_image_mime CHECK (mime_type IN ('image/jpeg','image/png')),
  CONSTRAINT ck_menu_image_ocr_status CHECK (ocr_status IN ('PENDING','PROCESSING','COMPLETED','FAILED'))
) ENGINE=InnoDB;

CREATE TABLE menu_image_items (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  menu_image_id BIGINT UNSIGNED NOT NULL,
  menu_id BIGINT UNSIGNED NULL,
  detected_name VARCHAR(100) NOT NULL,
  detected_category VARCHAR(50) NULL,
  detected_price BIGINT UNSIGNED NULL,
  sort_order INT UNSIGNED NOT NULL,
  confidence DECIMAL(5,4) NULL,
  price_review_required BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_menu_image_items_image_status (menu_image_id, status),
  KEY idx_menu_image_items_menu (menu_id),
  CONSTRAINT fk_menu_image_item_image FOREIGN KEY (menu_image_id) REFERENCES menu_images(id),
  CONSTRAINT fk_menu_image_item_menu FOREIGN KEY (menu_id) REFERENCES menus(id),
  CONSTRAINT ck_menu_image_item_price CHECK (detected_price IS NULL OR detected_price <= 1000000),
  CONSTRAINT ck_menu_image_item_confidence CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
  CONSTRAINT ck_menu_image_item_status CHECK (status IN ('PENDING','CONFIRMED','REJECTED')),
  CONSTRAINT ck_menu_image_item_confirmed_menu CHECK (status <> 'CONFIRMED' OR menu_id IS NOT NULL)
) ENGINE=InnoDB;
