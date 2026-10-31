-- Adds 3 package classification columns to the orders table
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS package_category VARCHAR(30) NOT NULL DEFAULT 'PARCEL',
    ADD COLUMN IF NOT EXISTS item_description VARCHAR(255),
    ADD COLUMN IF NOT EXISTS notes            VARCHAR(500);

COMMENT ON COLUMN orders.package_category IS 'Delivery category: FOOD, DOCUMENT, PARCEL, MEDICAL, FRAGILE';
COMMENT ON COLUMN orders.item_description  IS 'Human-readable description of the items being delivered';
COMMENT ON COLUMN orders.notes             IS 'Special instructions for the delivery driver';
