CREATE TABLE stored_file(id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant,original_name text NOT NULL,mime text NOT NULL,size_bytes integer NOT NULL CHECK(size_bytes BETWEEN 1 AND 2097152),created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(tenant_id,id));
ALTER TABLE product ADD COLUMN image_file_id uuid;
ALTER TABLE product ADD FOREIGN KEY(tenant_id,image_file_id) REFERENCES stored_file(tenant_id,id);
