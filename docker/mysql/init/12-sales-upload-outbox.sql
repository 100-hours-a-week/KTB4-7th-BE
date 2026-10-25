CREATE TABLE sales_upload_outbox (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id BIGINT UNSIGNED NOT NULL,
    upload_id BIGINT UNSIGNED NOT NULL,
    analysis_run_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'READY',
    checkpoint VARCHAR(24) NOT NULL DEFAULT 'ACCEPTED',
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    max_attempts INT UNSIGNED NOT NULL DEFAULT 5,
    available_at DATETIME(6) NOT NULL,
    claim_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_owner VARCHAR(100) NULL,
    lease_until DATETIME(6) NULL,
    applied_record_count INT UNSIGNED NULL,
    last_error_code VARCHAR(50) NULL,
    last_error_message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sales_outbox_event_upload (event_type, upload_id),
    KEY idx_sales_outbox_ready (status, available_at, id),
    KEY idx_sales_outbox_expired (status, lease_until, id),
    KEY idx_sales_outbox_store_order (store_id, status, id),
    KEY idx_sales_outbox_analysis_run (analysis_run_id),
    CONSTRAINT fk_sales_outbox_store FOREIGN KEY (store_id) REFERENCES stores(id),
    CONSTRAINT fk_sales_outbox_upload FOREIGN KEY (upload_id) REFERENCES sales_uploads(id),
    CONSTRAINT fk_sales_outbox_run FOREIGN KEY (analysis_run_id) REFERENCES analysis_runs(id),
    CONSTRAINT ck_sales_outbox_event CHECK (event_type = 'SALES_UPLOAD_BASIC_ANALYSIS'),
    CONSTRAINT ck_sales_outbox_status CHECK (status IN ('READY', 'RUNNING', 'DONE', 'DEAD')),
    CONSTRAINT ck_sales_outbox_checkpoint CHECK (checkpoint IN ('ACCEPTED', 'DATA_APPLIED', 'BASIC_COMPLETED')),
    CONSTRAINT ck_sales_outbox_attempts CHECK (max_attempts > 0 AND attempt_count <= max_attempts),
    CONSTRAINT ck_sales_outbox_lease CHECK (
        (status = 'RUNNING' AND claim_token IS NOT NULL AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'RUNNING' AND claim_token IS NULL AND lease_owner IS NULL AND lease_until IS NULL)
    ),
    CONSTRAINT ck_sales_outbox_applied CHECK (
        checkpoint = 'ACCEPTED' OR applied_record_count IS NOT NULL
    ),
    CONSTRAINT ck_sales_outbox_done CHECK (
        (status = 'DONE' AND checkpoint = 'BASIC_COMPLETED')
        OR (status <> 'DONE' AND checkpoint <> 'BASIC_COMPLETED')
    ),
    CONSTRAINT ck_sales_outbox_finished CHECK (
        (status IN ('DONE', 'DEAD') AND finished_at IS NOT NULL)
        OR (status IN ('READY', 'RUNNING') AND finished_at IS NULL)
    )
) ENGINE=InnoDB;
