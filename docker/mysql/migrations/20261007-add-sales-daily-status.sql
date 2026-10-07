-- Apply once to existing QA/production databases before deploying daily sales status handling.
ALTER TABLE sales_daily_summaries
  ADD COLUMN day_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' AFTER menu_quantity;
