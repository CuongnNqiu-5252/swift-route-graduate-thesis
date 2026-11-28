-- ==============================================================================
-- order_db: V4__extend_tenant_dispatch_and_weights.sql
-- Extend tenant dispatch configuration with driver pool toggle and normalize optimization weights
-- ==============================================================================

-- 1. Ensure allow_pool_drivers is defaulted in existing dispatch_config jsonb
UPDATE tenants
SET dispatch_config = dispatch_config || '{"allow_pool_drivers": false}'::jsonb
WHERE NOT (dispatch_config ? 'allow_pool_drivers');

-- 2. Normalize optimization_weights keys to time_weight and cost_weight
UPDATE tenants
SET optimization_weights = jsonb_build_object(
    'time_weight', COALESCE((optimization_weights->>'time_weight')::numeric, (optimization_weights->>'time')::numeric, 0.5),
    'cost_weight', COALESCE((optimization_weights->>'cost_weight')::numeric, (optimization_weights->>'cost')::numeric, (optimization_weights->>'distance')::numeric, 0.5)
)
WHERE optimization_weights IS NOT NULL;

