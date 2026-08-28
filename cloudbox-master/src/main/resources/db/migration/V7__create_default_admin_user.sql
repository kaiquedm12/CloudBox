INSERT INTO users (email, password_hash, role)
VALUES (
    'admin@admin.com',
    crypt('cloudbox', gen_salt('bf', 12)),
    'ADMIN'
)
ON CONFLICT (email) DO NOTHING;
