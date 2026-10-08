-- Existing QA/production stores.id is signed BIGINT, unlike the fresh-init schema.
CREATE TABLE ranking_profiles (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  store_id BIGINT NOT NULL,
  anonymous_nickname VARCHAR(50) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ranking_profiles_store (store_id),
  UNIQUE KEY uk_ranking_profiles_nickname (anonymous_nickname),
  CONSTRAINT fk_ranking_profiles_store FOREIGN KEY (store_id) REFERENCES stores(id)
) ENGINE=InnoDB;

CREATE TABLE ranking_snapshots (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  comparison_start DATE NOT NULL,
  comparison_end DATE NOT NULL,
  revision_no INT UNSIGNED NOT NULL DEFAULT 1,
  calculated_at DATETIME NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ranking_snapshots_period_revision (
    period_start, period_end, comparison_start, comparison_end, revision_no
  ),
  CONSTRAINT ck_ranking_snapshots_revision CHECK (revision_no > 0),
  CONSTRAINT ck_ranking_snapshots_period CHECK (period_start <= period_end),
  CONSTRAINT ck_ranking_snapshots_comparison CHECK (comparison_start <= comparison_end),
  CONSTRAINT ck_ranking_snapshots_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB;

CREATE TABLE ranking_entries (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  ranking_snapshot_id BIGINT UNSIGNED NOT NULL,
  ranking_profile_id BIGINT UNSIGNED NOT NULL,
  rank_no INT UNSIGNED NOT NULL,
  growth_rate DECIMAL(10,4) NOT NULL,
  selected_period_sales BIGINT UNSIGNED NOT NULL,
  comparison_period_sales BIGINT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ranking_entries_snapshot_profile (ranking_snapshot_id, ranking_profile_id),
  CONSTRAINT ck_ranking_entries_rank CHECK (rank_no > 0),
  CONSTRAINT fk_ranking_entries_snapshot FOREIGN KEY (ranking_snapshot_id) REFERENCES ranking_snapshots(id),
  CONSTRAINT fk_ranking_entries_profile FOREIGN KEY (ranking_profile_id) REFERENCES ranking_profiles(id)
) ENGINE=InnoDB;
