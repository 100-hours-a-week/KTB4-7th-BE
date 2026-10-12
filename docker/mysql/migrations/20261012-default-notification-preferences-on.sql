ALTER TABLE notification_preferences
  ALTER COLUMN solution_enabled SET DEFAULT TRUE,
  ALTER COLUMN sales_upload_reminder_enabled SET DEFAULT TRUE;
