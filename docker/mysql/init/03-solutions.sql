CREATE TABLE solution_bundles (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT UNSIGNED NOT NULL,
  sales_analysis_id BIGINT UNSIGNED NOT NULL,
  target_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_solution_bundle_store_date (store_id, target_date),
  KEY idx_solution_bundle_analysis (sales_analysis_id),
  CONSTRAINT chk_solution_bundle_status
    CHECK (status IN ('PENDING', 'GENERATING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB;

CREATE TABLE solutions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT UNSIGNED NOT NULL,
  sales_analysis_id BIGINT UNSIGNED NOT NULL,
  solution_bundle_id BIGINT UNSIGNED NOT NULL,
  solution_type VARCHAR(30) NOT NULL,
  title VARCHAR(200) NOT NULL,
  summary_text TEXT NOT NULL,
  detail_text TEXT NOT NULL,
  evidence_text TEXT NULL,
  generated_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  rank_no INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_solutions_bundle_rank (solution_bundle_id, rank_no),
  KEY idx_solutions_store_created (store_id, created_at),
  CONSTRAINT chk_solutions_rank CHECK (rank_no > 0),
  CONSTRAINT chk_solutions_type CHECK (solution_type IN ('DAILY', 'MANUAL')),
  CONSTRAINT fk_solutions_bundle
    FOREIGN KEY (solution_bundle_id) REFERENCES solution_bundles(id)
) ENGINE=InnoDB;

CREATE TABLE saved_solutions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  solution_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_saved_solutions_user_solution (user_id, solution_id),
  KEY idx_saved_solutions_user_created (user_id, created_at),
  CONSTRAINT fk_saved_solutions_user
    FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_saved_solutions_solution
    FOREIGN KEY (solution_id) REFERENCES solutions(id)
) ENGINE=InnoDB;

CREATE TABLE chat_messages (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  solution_bundle_id BIGINT UNSIGNED NULL,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  evidence_json JSON NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_chat_messages_user_created (user_id, created_at),
  KEY idx_chat_messages_bundle_created (solution_bundle_id, created_at),
  CONSTRAINT fk_chat_message_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_chat_message_solution_bundle FOREIGN KEY (solution_bundle_id) REFERENCES solution_bundles(id),
  CONSTRAINT ck_chat_message_role CHECK (role IN ('USER','ASSISTANT')),
  CONSTRAINT ck_chat_message_status CHECK (status IN ('PENDING','STREAMING','COMPLETED','FAILED'))
) ENGINE=InnoDB;
