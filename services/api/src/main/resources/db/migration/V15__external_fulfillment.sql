ALTER TABLE sales_order ADD COLUMN platform_fulfillment_status text NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE sales_order ADD COLUMN externally_fulfilled boolean NOT NULL DEFAULT false;
