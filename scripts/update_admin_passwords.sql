UPDATE users
SET password_hash = '$2a$10$LuxS4Tym8CH9ZwLDX8fHru7fUWkITTcrUsykZO/dlohMNzMsms9n6',
    role = 'ADMIN',
    tenant_id = 'a1111111-1111-1111-1111-111111111111',
    status = 'ACTIVE'
WHERE email IN ('admin@swiftroute.vn', 'admin@gmail.com');

INSERT INTO users (user_id, email, phone, password_hash, full_name, role, tenant_id, status, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@gmail.com',
    '0909000001',
    '$2a$10$LuxS4Tym8CH9ZwLDX8fHru7fUWkITTcrUsykZO/dlohMNzMsms9n6',
    'Quản Trị Viên Hệ Thống',
    'ADMIN',
    'a1111111-1111-1111-1111-111111111111',
    'ACTIVE',
    now()
) ON CONFLICT (email) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role,
    tenant_id = EXCLUDED.tenant_id,
    status = 'ACTIVE';

INSERT INTO tenant_memberships (membership_id, tenant_id, user_id, membership_role, status, created_at, updated_at)
SELECT gen_random_uuid(), 'a1111111-1111-1111-1111-111111111111', u.user_id, 'OWNER', 'ACTIVE', now(), now()
FROM users u
WHERE u.email IN ('admin@swiftroute.vn', 'admin@gmail.com')
AND NOT EXISTS (
    SELECT 1 FROM tenant_memberships tm
    WHERE tm.user_id = u.user_id AND tm.tenant_id = 'a1111111-1111-1111-1111-111111111111'
);

