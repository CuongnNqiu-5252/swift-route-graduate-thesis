-- Drop redundant address column from tenants; physical warehouse addresses and
-- coordinates are stored authoritatively in pickup_locations.
ALTER TABLE tenants
    DROP COLUMN IF EXISTS address;
