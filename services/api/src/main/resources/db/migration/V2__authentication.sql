CREATE TABLE app_user(username text PRIMARY KEY,password_hash text NOT NULL,enabled boolean NOT NULL DEFAULT true);
ALTER TABLE membership DROP CONSTRAINT membership_pkey;
ALTER TABLE membership ADD PRIMARY KEY(username,tenant_id);
-- Existing membership rows must be provisioned with credentials before login.
