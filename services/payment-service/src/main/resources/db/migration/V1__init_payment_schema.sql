CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE payments (
  payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID NOT NULL,
  customer_id UUID NOT NULL,
  tenant_id UUID,
  amount DECIMAL(12, 2) NOT NULL CHECK (amount >= 0),
  currency VARCHAR(3) NOT NULL DEFAULT 'VND',
  gateway VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  mode VARCHAR(20) NOT NULL,
  txn_ref VARCHAR(64) NOT NULL UNIQUE,
  gateway_txn_id VARCHAR(64),
  payment_url TEXT,
  paid_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_payments_order_id ON payments(order_id);

CREATE TABLE payment_webhook_logs (
  log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_id UUID REFERENCES payments(payment_id),
  gateway VARCHAR(20) NOT NULL,
  raw_payload JSONB NOT NULL,
  signature_valid BOOLEAN NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
