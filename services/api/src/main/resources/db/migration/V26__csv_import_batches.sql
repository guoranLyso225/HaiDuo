CREATE TABLE csv_import_batch(
 id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant,warehouse_id uuid NOT NULL,actor text NOT NULL,
 request_key uuid NOT NULL,content_hash text NOT NULL,imported integer NOT NULL DEFAULT 0,duplicates integer NOT NULL DEFAULT 0,
 status text NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','ROLLED_BACK')),created_at timestamptz NOT NULL DEFAULT now(),rolled_back_at timestamptz,
 UNIQUE(tenant_id,id),UNIQUE(tenant_id,request_key),FOREIGN KEY(tenant_id,warehouse_id) REFERENCES warehouse(tenant_id,id));
CREATE TABLE csv_import_order(tenant_id uuid NOT NULL,batch_id uuid NOT NULL,order_id uuid NOT NULL,
 PRIMARY KEY(tenant_id,order_id),FOREIGN KEY(tenant_id,batch_id) REFERENCES csv_import_batch(tenant_id,id),FOREIGN KEY(tenant_id,order_id) REFERENCES sales_order(tenant_id,id));
CREATE INDEX csv_import_batch_orders ON csv_import_order(tenant_id,batch_id);
ALTER TABLE sales_order ADD COLUMN import_voided boolean NOT NULL DEFAULT false;
ALTER TABLE inventory_correction DROP CONSTRAINT inventory_correction_kind_check;
ALTER TABLE inventory_correction ADD CHECK(kind IN('RELEASE','RECEIPT_REVERSE','CSV_ROLLBACK'));
