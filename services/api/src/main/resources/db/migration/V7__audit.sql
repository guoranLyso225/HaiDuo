CREATE TABLE audit_log(id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant,actor text NOT NULL,action text NOT NULL,target_id text NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX audit_tenant_time ON audit_log(tenant_id,created_at DESC);
