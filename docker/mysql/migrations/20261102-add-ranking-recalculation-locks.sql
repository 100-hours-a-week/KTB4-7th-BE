CREATE TABLE ranking_recalculation_locks (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  comparison_start DATE NOT NULL,
  comparison_end DATE NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ranking_recalculation_locks_period (
    period_start, period_end, comparison_start, comparison_end
  ),
  CONSTRAINT ck_ranking_recalculation_locks_period CHECK (period_start <= period_end),
  CONSTRAINT ck_ranking_recalculation_locks_comparison CHECK (comparison_start <= comparison_end)
) ENGINE=InnoDB;
