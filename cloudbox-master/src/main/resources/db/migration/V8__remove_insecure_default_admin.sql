DELETE FROM users
WHERE email = 'admin@admin.com'
  AND password_hash = crypt('cloudbox', password_hash);
