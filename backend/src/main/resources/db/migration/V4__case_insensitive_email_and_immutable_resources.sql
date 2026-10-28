CREATE UNIQUE INDEX app_users_email_lower_unique ON app_users (lower(email));
ALTER TABLE goals DROP COLUMN version;
ALTER TABLE plans DROP COLUMN version;
