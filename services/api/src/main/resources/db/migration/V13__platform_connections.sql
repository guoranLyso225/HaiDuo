ALTER TABLE store DROP CONSTRAINT store_connection_status_check;
ALTER TABLE store ADD CHECK(connection_status IN ('MOCK','DISABLED','CONNECTED'));
CREATE TABLE platform_connection(tenant_id uuid NOT NULL,store_id uuid NOT NULL,shop_domain text NOT NULL UNIQUE,access_cipher text NOT NULL,refresh_cipher text,expires_at timestamptz,scopes text NOT NULL,PRIMARY KEY(tenant_id,store_id),FOREIGN KEY(tenant_id,store_id) REFERENCES store(tenant_id,id));
CREATE TABLE webhook_inbox(id uuid PRIMARY KEY,tenant_id uuid NOT NULL,store_id uuid NOT NULL,delivery_id text NOT NULL,event_id text NOT NULL,topic text NOT NULL,order_gid text NOT NULL,payload_hash text NOT NULL,status text NOT NULL DEFAULT 'RECEIVED',created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(tenant_id,store_id,delivery_id),UNIQUE(tenant_id,store_id,event_id,topic),FOREIGN KEY(tenant_id,store_id) REFERENCES store(tenant_id,id));
ALTER TABLE sales_order ADD COLUMN source_updated_at timestamptz;
