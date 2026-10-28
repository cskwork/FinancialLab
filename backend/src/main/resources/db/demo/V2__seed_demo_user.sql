-- Public demo account only. Password: FinLabDemo!2026 (bcrypt cost 12).
-- Replace/disable this account before exposing a production instance.
INSERT INTO app_users (email, password_hash, role)
VALUES ('demo@finlab.app', '{bcrypt}$2y$12$wDeuccX2yU1eg/zSexUeHOZK0V..SbqUsXIOtn8Xz7nQlhiGFxfdG', 'USER');
