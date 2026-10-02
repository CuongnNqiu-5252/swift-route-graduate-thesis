-- V2__create_orders_table.sql
CREATE TABLE orders (
    order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(tenant_id),
    customer_id UUID NOT NULL,             -- tham chiếu logic sang User bên Auth Service, KHÔNG FK vật lý
    pickup_lat DOUBLE PRECISION NOT NULL,
    pickup_lng DOUBLE PRECISION NOT NULL,
    delivery_lat DOUBLE PRECISION NOT NULL,
    delivery_lng DOUBLE PRECISION NOT NULL,
    delivery_address VARCHAR(500),
    time_window_start TIME NOT NULL,
    time_window_end TIME NOT NULL,
    weight_kg DOUBLE PRECISION NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        CHECK (priority IN ('NORMAL','HIGH','URGENT')),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT'
        CHECK (status IN ('PENDING_PAYMENT','PAYMENT_FAILED','PAID','PLANNED',
                           'IN_TRANSIT','DELIVERED','FAILED','CANCELLED')),
    assigned_driver_id UUID,               -- tham chiếu logic sang Driver Service
    plan_id UUID,                          -- gán khi Routing Engine lập kế hoạch xong
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_tenant_status ON orders(tenant_id, status);
