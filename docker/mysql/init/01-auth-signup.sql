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
