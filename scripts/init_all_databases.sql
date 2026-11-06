-- ==============================================================================
-- SwiftRoute: Provision All 6 Target Microservice Databases
-- ==============================================================================

SELECT 'CREATE DATABASE auth_db WITH OWNER openpg ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth_db')\gexec

SELECT 'CREATE DATABASE order_db WITH OWNER openpg ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'order_db')\gexec

SELECT 'CREATE DATABASE driver_db WITH OWNER openpg ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'driver_db')\gexec

SELECT 'CREATE DATABASE payment_db WITH OWNER openpg ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'payment_db')\gexec

SELECT 'CREATE DATABASE routing_db WITH OWNER openpg ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'routing_db')\gexec

SELECT 'CREATE DATABASE notification_db WITH OWNER openpg ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notification_db')\gexec

