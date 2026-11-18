-- Align notification_deliveries channel constraint with implemented channels (remove unused SMS).
ALTER TABLE notification_deliveries DROP CONSTRAINT IF EXISTS notification_deliveries_channel_check;

DELETE FROM notification_deliveries WHERE channel = 'SMS';

ALTER TABLE notification_deliveries ADD CONSTRAINT notification_deliveries_channel_check CHECK (
    channel IN ('PUSH', 'EMAIL', 'IN_APP')
);
