CREATE TABLE fcm_tokens (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  token_value VARCHAR(4096) NOT NULL,
  token_hash CHAR(64) NOT NULL,
  last_registered_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fcm_tokens_hash (token_hash),
  KEY idx_fcm_tokens_user (user_id, id),
  CONSTRAINT fk_fcm_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE fcm_deliveries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  notification_id BIGINT UNSIGNED NOT NULL,
  fcm_token_id BIGINT UNSIGNED NULL,
  token_hash CHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
  last_attempt_at DATETIME NULL,
  sent_at DATETIME NULL,
  last_error_code VARCHAR(100) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fcm_deliveries_notification_token (notification_id, token_hash),
  KEY idx_fcm_deliveries_status_created (status, created_at, id),
  KEY idx_fcm_deliveries_notification (notification_id, id),
  CONSTRAINT fk_fcm_deliveries_notification FOREIGN KEY (notification_id) REFERENCES notifications(id),
  CONSTRAINT fk_fcm_deliveries_token FOREIGN KEY (fcm_token_id) REFERENCES fcm_tokens(id) ON DELETE SET NULL,
  CONSTRAINT ck_fcm_deliveries_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
) ENGINE=InnoDB;
