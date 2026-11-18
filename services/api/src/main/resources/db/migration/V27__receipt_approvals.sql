CREATE TABLE receipt_approval (
 tenant_id uuid NOT NULL REFERENCES tenant, request_key uuid NOT NULL,
 applicant text NOT NULL, sku_id uuid NOT NULL, warehouse_id uuid NOT NULL,
 quantity integer NOT NULL CHECK(quantity BETWEEN 1 AND 1000000),
 status text NOT NULL CHECK(status IN('PENDING','APPLIED','REJECTED','WITHDRAWN','EXPIRED')),
 reviewer text, comment text, created_at timestamptz NOT NULL DEFAULT now(),
 expires_at timestamptz NOT NULL DEFAULT now()+interval '24 hours', reviewed_at timestamptz,
 PRIMARY KEY(tenant_id,request_key),
 FOREIGN KEY(tenant_id,sku_id,warehouse_id) REFERENCES inventory(tenant_id,sku_id,warehouse_id)
);
CREATE INDEX receipt_approval_created ON receipt_approval(tenant_id,created_at);
