-- Update fulfillment_status and payment_status based on legacy status
UPDATE orders SET
    fulfillment_status = CASE
        WHEN status = 'PENDING_PAYMENT' THEN 'DRAFT'
        WHEN status = 'PAYMENT_FAILED'  THEN 'CANCELLED'
        WHEN status = 'PAID'            THEN 'READY_FOR_PLANNING'
        WHEN status = 'PLANNED'         THEN 'PLANNED'
        WHEN status = 'IN_TRANSIT'      THEN 'IN_TRANSIT'
        WHEN status = 'DELIVERED'       THEN 'DELIVERED'
        WHEN status = 'FAILED'          THEN 'FAILED'
        WHEN status = 'CANCELLED'       THEN 'CANCELLED'
        ELSE NULL
    END,
    payment_status = CASE
        WHEN status = 'PENDING_PAYMENT' THEN 'PENDING'
        WHEN status = 'PAYMENT_FAILED'  THEN 'FAILED'
        WHEN status = 'PAID'            THEN 'PAID'
        WHEN status = 'PLANNED'         THEN 'PAID'
        WHEN status = 'IN_TRANSIT'      THEN 'PAID'
        WHEN status = 'DELIVERED'       THEN 'PAID'
        WHEN status = 'FAILED'          THEN 'FAILED'
        WHEN status = 'CANCELLED'       THEN 'CANCELLED'
        ELSE NULL
    END;

-- Assert that no unmapped rows remain
DO $$
DECLARE
    v_unmapped_count INT;
BEGIN
    SELECT COUNT(*) INTO v_unmapped_count
    FROM orders
    WHERE fulfillment_status IS NULL OR payment_status IS NULL;

    IF v_unmapped_count > 0 THEN
        RAISE EXCEPTION 'MIGRATION ABORTED: Phát hiện % đơn hàng có trạng thái legacy không hợp lệ!', v_unmapped_count;
    END IF;
END $$;

