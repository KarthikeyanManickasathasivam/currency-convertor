UPDATE users
SET role = 'ADMIN', updated_at = NOW()
WHERE email = 'cartkn.kkdi@gmail.com' AND role != 'ADMIN';
