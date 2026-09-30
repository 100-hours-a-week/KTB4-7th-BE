-- Purpose: allow stores created without a business registration number or verification.
--
-- Run once on existing QA/production databases before deploying the matching BE version.
-- MySQL UNIQUE indexes permit multiple NULL values, so verified business registration
-- numbers remain unique while stores without a number can be created.

ALTER TABLE stores
  MODIFY COLUMN business_registration_no CHAR(10) NULL,
  MODIFY COLUMN business_verified_at DATETIME(6) NULL;
