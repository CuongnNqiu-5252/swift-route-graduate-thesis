-- Remove unused placeholder notification tables; deliveries are served via notification_deliveries.
DROP TABLE IF EXISTS notification_preferences;
DROP TABLE IF EXISTS device_tokens;
