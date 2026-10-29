-- Existing databases only; run BEFORE 20261027-unify-sales-menu-categories.sql.
-- Stop BE/readers/upload and analysis workers, drain in-flight work, then back up
-- data AND SHOW CREATE TABLE sales_order_items before running this DDL.
-- The legacy native ENUM does not accept BEVERAGE/OTHER. Preserve its current
-- string values first; the separate category migration performs the backfill
-- and adds the six-category CHECK afterwards. Fresh installs already use VARCHAR.
-- MySQL DDL commits implicitly; ROLLBACK cannot undo this type change.
ALTER TABLE sales_order_items
  MODIFY COLUMN standard_category VARCHAR(30) NOT NULL;
