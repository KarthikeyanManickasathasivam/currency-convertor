INSERT INTO users (user_id, username, email, password_hash, role, is_active, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'admin',
    'cartkn.kkdi@gmail.com',
    '$2a$12$PrRGbENra1tQ2hEJLMd4AuVg56vMOfMBqaEJvvcR6LqUTOQC48g.6',
    'ADMIN',
    TRUE,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;
