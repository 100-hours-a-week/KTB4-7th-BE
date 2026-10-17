CREATE TABLE seoul_district_quarter_imports (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  basis_quarter CHAR(6) NOT NULL,
  industry_code CHAR(8) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_seoul_district_import_quarter_industry (basis_quarter, industry_code),
  CONSTRAINT ck_seoul_district_import_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
  CONSTRAINT ck_seoul_district_import_completed CHECK (completed_at IS NULL OR status = 'COMPLETED')
) ENGINE=InnoDB;

CREATE TABLE seoul_district_quarter_benchmarks (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  import_id BIGINT UNSIGNED NOT NULL,
  administrative_dong_code CHAR(10) NOT NULL,
  cafe_store_count INT UNSIGNED NOT NULL,
  district_avg_monthly_sales_per_cafe BIGINT UNSIGNED NOT NULL,
  peak_sales_hour_band CHAR(5) NOT NULL,
  peak_foot_traffic_hour_band CHAR(5) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_seoul_district_benchmark_import_dong (import_id, administrative_dong_code),
  CONSTRAINT fk_seoul_district_benchmark_import FOREIGN KEY (import_id) REFERENCES seoul_district_quarter_imports(id),
  CONSTRAINT ck_seoul_district_benchmark_cafe_count CHECK (cafe_store_count >= 3),
  CONSTRAINT ck_seoul_district_benchmark_monthly_sales CHECK (district_avg_monthly_sales_per_cafe >= 0),
  CONSTRAINT ck_seoul_district_benchmark_sales_band CHECK (peak_sales_hour_band IN ('00-06', '06-11', '11-14', '14-17', '17-21', '21-24')),
  CONSTRAINT ck_seoul_district_benchmark_traffic_band CHECK (peak_foot_traffic_hour_band IN ('00-06', '06-11', '11-14', '14-17', '17-21', '21-24'))
) ENGINE=InnoDB;
