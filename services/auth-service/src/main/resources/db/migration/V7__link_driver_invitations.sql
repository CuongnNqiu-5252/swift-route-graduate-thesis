ALTER TABLE tenant_invitations ADD COLUMN IF NOT EXISTS driver_id UUID;
ALTER TABLE tenant_invitations ADD COLUMN IF NOT EXISTS business_name VARCHAR(255);
