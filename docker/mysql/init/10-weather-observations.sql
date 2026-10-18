CREATE TABLE weather_stations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  source VARCHAR(50) NOT NULL,
  station_code VARCHAR(50) NOT NULL,
  latitude DECIMAL(10,7) NOT NULL,
  longitude DECIMAL(10,7) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_weather_station_source_code (source, station_code),
  CONSTRAINT ck_weather_station_latitude CHECK (latitude BETWEEN -90 AND 90),
  CONSTRAINT ck_weather_station_longitude CHECK (longitude BETWEEN -180 AND 180)
) ENGINE=InnoDB;

ALTER TABLE stores
  ADD COLUMN weather_station_id BIGINT UNSIGNED NULL,
  ADD KEY idx_stores_weather_station_id (weather_station_id),
  ADD CONSTRAINT fk_stores_weather_station
    FOREIGN KEY (weather_station_id) REFERENCES weather_stations(id);

CREATE TABLE weather_observations (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  station_id BIGINT UNSIGNED NOT NULL,
  observation_date DATE NOT NULL,
  latitude DECIMAL(10,7) NOT NULL,
  longitude DECIMAL(10,7) NOT NULL,
  weather_code VARCHAR(30) NOT NULL,
  temperature_celsius DECIMAL(5,2) NULL,
  precipitation_mm DECIMAL(8,2) NULL,
  source VARCHAR(50) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_weather_observation_station_date (station_id, observation_date),
  CONSTRAINT fk_weather_observation_station FOREIGN KEY (station_id) REFERENCES weather_stations(id),
  CONSTRAINT ck_weather_observation_latitude CHECK (latitude BETWEEN -90 AND 90),
  CONSTRAINT ck_weather_observation_longitude CHECK (longitude BETWEEN -180 AND 180),
  CONSTRAINT ck_weather_observation_precipitation CHECK (precipitation_mm IS NULL OR precipitation_mm >= 0)
) ENGINE=InnoDB;
