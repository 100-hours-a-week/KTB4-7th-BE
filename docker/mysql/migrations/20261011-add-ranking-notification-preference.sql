ALTER TABLE notification_preferences
  ADD COLUMN ranking_change_enabled BOOLEAN NOT NULL DEFAULT TRUE;
