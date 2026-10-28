-- Calendar uses the existing order-date signed POS sales policy (user instruction 2026-10-28).
-- Do not invent paid_at/refund timestamps or change historical coverage status.
ALTER TABLE sales_daily_summaries
  ADD COLUMN sales_activity_count INT NOT NULL DEFAULT 0 AFTER order_count;

UPDATE sales_daily_summaries AS summary
JOIN (
  SELECT store_id, DATE(ordered_at) AS sales_date, COUNT(*) AS activity_count
  FROM sales_orders GROUP BY store_id, DATE(ordered_at)
) AS activity ON activity.store_id = summary.store_id AND activity.sales_date = summary.sales_date
SET summary.sales_activity_count = activity.activity_count;

CREATE TABLE weather_calendar_conditions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  station_id BIGINT UNSIGNED NOT NULL,
  observation_date DATE NOT NULL,
  `condition` VARCHAR(10) NOT NULL,
  observed_at DATETIME NOT NULL,
  source VARCHAR(50) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_weather_calendar_condition_station_date (station_id, observation_date),
  CONSTRAINT fk_weather_calendar_condition_station
    FOREIGN KEY (station_id) REFERENCES weather_stations(id),
  CONSTRAINT ck_weather_calendar_condition
    CHECK (`condition` IN ('SUNNY', 'CLOUDY', 'RAIN', 'SNOW'))
) ENGINE=InnoDB;
