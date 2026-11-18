CREATE TABLE business_request(
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant,applicant text NOT NULL,
 kind text NOT NULL,target_id text NOT NULL,payload text NOT NULL,before_state text NOT NULL,
 request_hash text NOT NULL,status text NOT NULL DEFAULT 'PENDING'
 CHECK(status IN('PENDING','APPLIED','REJECTED','WITHDRAWN','EXPIRED','STALE')),
 reviewer text,comment text,result text,created_at timestamptz NOT NULL DEFAULT now(),
 expires_at timestamptz NOT NULL DEFAULT now()+interval '24 hours',reviewed_at timestamptz,
 UNIQUE(tenant_id,applicant,request_hash)
);
CREATE INDEX business_request_created ON business_request(tenant_id,created_at);
CREATE TABLE product_control(
 tenant_id uuid NOT NULL,product_id uuid NOT NULL,archived boolean NOT NULL DEFAULT false,
 version bigint NOT NULL DEFAULT 0,PRIMARY KEY(tenant_id,product_id),
 FOREIGN KEY(tenant_id,product_id) REFERENCES product(tenant_id,id)
);
CREATE TABLE sku_alias(
 tenant_id uuid NOT NULL,code text NOT NULL,sku_id uuid NOT NULL,
 PRIMARY KEY(tenant_id,code),FOREIGN KEY(tenant_id,sku_id) REFERENCES sku(tenant_id,id)
);
INSERT INTO sku_alias SELECT tenant_id,code,id FROM sku;
CREATE TABLE business_adjustment(
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant,kind text NOT NULL,
 target_id uuid NOT NULL,sku_id uuid NOT NULL,warehouse_id uuid NOT NULL,
 quantity integer NOT NULL,reason text NOT NULL,actor text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(tenant_id,sku_id,warehouse_id) REFERENCES inventory(tenant_id,sku_id,warehouse_id)
);
CREATE TABLE business_history(
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant,kind text NOT NULL,
 target_id text NOT NULL,before_state text NOT NULL,after_state text NOT NULL,
 actor text NOT NULL,reason text NOT NULL,created_at timestamptz NOT NULL DEFAULT now()
);
