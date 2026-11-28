-- ==============================================================================
-- order_db: V15__drop_obsolete_products_and_product_categories.sql
-- Drop obsolete product catalog tables:
-- 1. products
-- 2. product_categories
--
-- SwiftRoute operates exclusively as a Field-Ops & SaaS Delivery Operations platform
-- where delivery waybills (orders) are package-level manifests without product SKUs.
-- ==============================================================================

DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS product_categories CASCADE;

