ALTER TABLE sales_order ADD COLUMN inventory_revision bigint NOT NULL DEFAULT 0;
ALTER TABLE inventory_movement ADD COLUMN cycle bigint NOT NULL DEFAULT 0;
ALTER TABLE inventory_movement DROP CONSTRAINT inventory_movement_order_id_sku_id_kind_key;
ALTER TABLE inventory_movement ADD CONSTRAINT movement_cycle_unique UNIQUE(order_id,sku_id,kind,cycle);
CREATE TABLE inventory_correction(
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant,kind text NOT NULL CHECK(kind IN('RELEASE','RECEIPT_REVERSE')),
 target_id uuid NOT NULL,expected_revision bigint NOT NULL,applicant text NOT NULL,request_key uuid NOT NULL,reason text NOT NULL,
 status text NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','APPLIED','REJECTED','WITHDRAWN','EXPIRED','STALE')),
 reviewer text,comment text,created_at timestamptz NOT NULL DEFAULT now(),expires_at timestamptz NOT NULL DEFAULT now()+interval '24 hours',reviewed_at timestamptz,
 UNIQUE(tenant_id,applicant,request_key),UNIQUE(tenant_id,id));
CREATE INDEX correction_tenant_created ON inventory_correction(tenant_id,created_at);
CREATE TABLE receipt_reversal(
 tenant_id uuid NOT NULL,receipt_key uuid NOT NULL,correction_id uuid NOT NULL,sku_id uuid NOT NULL,warehouse_id uuid NOT NULL,
 quantity integer NOT NULL CHECK(quantity>0),created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(tenant_id,receipt_key),UNIQUE(tenant_id,correction_id),
 FOREIGN KEY(tenant_id,receipt_key) REFERENCES stock_receipt(tenant_id,request_key),
 FOREIGN KEY(tenant_id,correction_id) REFERENCES inventory_correction(tenant_id,id),
 FOREIGN KEY(tenant_id,sku_id,warehouse_id) REFERENCES inventory(tenant_id,sku_id,warehouse_id));
