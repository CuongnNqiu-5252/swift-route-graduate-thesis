-- Email-only invitations create an identity-linked draft before vehicle details exist.
ALTER TABLE drivers ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE drivers ALTER COLUMN vehicle_type DROP NOT NULL;
