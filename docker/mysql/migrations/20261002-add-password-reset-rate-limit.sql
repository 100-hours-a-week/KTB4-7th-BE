-- Apply once to an existing QA/production database before deploying the BE version
-- that enables password-reset email request limits.
CREATE TABLE IF NOT EXISTS password_reset_email_attempts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  ip_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  requested_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_password_reset_attempts_email_requested (email_hash, requested_at),
  KEY idx_password_reset_attempts_ip_requested (ip_hash, requested_at),
  KEY idx_password_reset_attempts_requested_at (requested_at)
) ENGINE=InnoDB;
