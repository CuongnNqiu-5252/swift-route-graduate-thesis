ALTER TABLE orders
    ALTER COLUMN time_window_start DROP NOT NULL,
    ALTER COLUMN time_window_end DROP NOT NULL,
    ADD CONSTRAINT orders_appointment_pair_check
        CHECK ((time_window_start IS NULL) = (time_window_end IS NULL));
