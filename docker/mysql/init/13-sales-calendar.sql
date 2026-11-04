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
