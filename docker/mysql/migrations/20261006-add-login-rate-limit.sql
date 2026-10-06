-- Apply once to existing QA/production databases before deploying the login rate limit.
CREATE TABLE IF NOT EXISTS login_attempts (
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
