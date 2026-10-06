CREATE TABLE users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  last_login_at DATETIME NULL,
  deleted_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_phone (phone)
) ENGINE=InnoDB;

CREATE TABLE signup_drafts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  signup_token_hash CHAR(64) NOT NULL,
  email VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  terms_of_service_agreed BOOLEAN NOT NULL,
  terms_of_service_version VARCHAR(50) NOT NULL,
  privacy_policy_agreed BOOLEAN NOT NULL,
  privacy_policy_version VARCHAR(50) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_signup_drafts_token_hash (signup_token_hash),
  KEY idx_signup_drafts_expires_at (expires_at)
) ENGINE=InnoDB;

CREATE TABLE password_reset_tokens (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash CHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_password_reset_tokens_user
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE password_reset_email_attempts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  ip_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  requested_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_password_reset_attempts_email_requested (email_hash, requested_at),
  KEY idx_password_reset_attempts_ip_requested (ip_hash, requested_at),
  KEY idx_password_reset_attempts_requested_at (requested_at)
) ENGINE=InnoDB;

CREATE TABLE login_attempts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ip_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  failed_at DATETIME(6) NOT NULL,
  account_blocked_until DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_login_attempts_email_failed (email_hash, failed_at),
  KEY idx_login_attempts_email_blocked_until (email_hash, account_blocked_until),
  KEY idx_login_attempts_ip_failed (ip_hash, failed_at),
  KEY idx_login_attempts_failed_at (failed_at)
) ENGINE=InnoDB;

CREATE TABLE business_verifications (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  business_reg_number CHAR(10) NOT NULL,
  verified_at DATETIME NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_business_verifications_expires_at (expires_at)
) ENGINE=InnoDB;

CREATE TABLE stores (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_user_id BIGINT UNSIGNED NOT NULL,
  business_registration_no CHAR(10) NULL,
  business_verified_at DATETIME NULL,
  name VARCHAR(100) NOT NULL,
  postal_code CHAR(5) NOT NULL,
  address VARCHAR(255) NOT NULL,
  address_detail VARCHAR(255) NULL,
  latitude DECIMAL(10,7) NULL,
  longitude DECIMAL(10,7) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stores_owner_user_id (owner_user_id),
  UNIQUE KEY uk_stores_business_registration_no (business_registration_no),
  CONSTRAINT fk_stores_owner_user
    FOREIGN KEY (owner_user_id) REFERENCES users(id),
  CONSTRAINT chk_stores_latitude
    CHECK (latitude BETWEEN -90 AND 90),
  CONSTRAINT chk_stores_longitude
    CHECK (longitude BETWEEN -180 AND 180),
  CONSTRAINT chk_stores_status
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
) ENGINE=InnoDB;

CREATE TABLE store_business_hours (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT UNSIGNED NOT NULL,
  day_of_week INT NOT NULL,
  opens_at TIME NULL,
  closes_at TIME NULL,
  is_closed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_business_hours_store_day (store_id, day_of_week),
  CONSTRAINT fk_store_business_hours_store
    FOREIGN KEY (store_id) REFERENCES stores(id),
  CONSTRAINT chk_store_business_hours_day_of_week
    CHECK (day_of_week BETWEEN 1 AND 7)
) ENGINE=InnoDB;

CREATE TABLE notification_preferences (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  solution_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  sales_upload_reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_preferences_user_id (user_id),
  CONSTRAINT fk_notification_preferences_user
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;
