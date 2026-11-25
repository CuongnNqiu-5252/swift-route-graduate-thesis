-- Bank account used to render VietQR payout codes on driver settlements.
-- All columns are nullable: existing drivers keep working and can be filled in later.
ALTER TABLE drivers
    ADD COLUMN IF NOT EXISTS bank_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(40),
    ADD COLUMN IF NOT EXISTS bank_account_name VARCHAR(120);
