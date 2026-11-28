ALTER TABLE cod_handover_batches
    ADD COLUMN IF NOT EXISTS variance_resolution VARCHAR(30);

ALTER TABLE cod_handover_batches
    DROP CONSTRAINT IF EXISTS cod_handover_batches_status_check;

ALTER TABLE cod_handover_batches
    ADD CONSTRAINT cod_handover_batches_status_check
    CHECK (status IN (
        'DRAFT',
        'SUBMITTED',
        'SETTLED',
        'SETTLED_WITH_VARIANCE',
        'VERIFIED',
        'DISCREPANCY',
        'RESOLVED',
        'CANCELLED'
    ));

ALTER TABLE cod_handover_batches
    DROP CONSTRAINT IF EXISTS cod_handover_batches_variance_resolution_check;

ALTER TABLE cod_handover_batches
    ADD CONSTRAINT cod_handover_batches_variance_resolution_check
    CHECK (variance_resolution IS NULL OR variance_resolution IN (
        'DRIVER_RESPONSIBLE',
        'BUSINESS_ADJUSTMENT',
        'OTHER'
    ));

UPDATE cod_handover_batches
SET status = 'SETTLED'
WHERE status = 'VERIFIED';

UPDATE cod_handover_batches
SET status = 'SETTLED_WITH_VARIANCE',
    variance_resolution = COALESCE(variance_resolution, 'OTHER')
WHERE status = 'RESOLVED';
