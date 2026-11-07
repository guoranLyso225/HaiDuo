ALTER TABLE order_item ADD COLUMN unit_cost_cny numeric(19,4);
-- Legacy orders have no reliable historical cost; do not infer it from current SKU prices.
CREATE TABLE order_fee(tenant_id uuid NOT NULL,order_id uuid NOT NULL,kind text NOT NULL CHECK(kind IN ('FREIGHT','PLATFORM')),amount_cny numeric(19,4) NOT NULL CHECK(amount_cny>=0),PRIMARY KEY(tenant_id,order_id,kind),FOREIGN KEY(tenant_id,order_id) REFERENCES sales_order(tenant_id,id));
