ALTER TABLE sales_order ADD COLUMN payment_status text NOT NULL DEFAULT 'UNKNOWN' CHECK(payment_status IN ('UNKNOWN','PENDING','PAID','FAILED'));
ALTER TABLE sales_order ADD COLUMN refund_status text NOT NULL DEFAULT 'NONE' CHECK(refund_status IN ('NONE','PENDING','PARTIAL','REFUNDED'));
-- Legacy CSV does not contain payment evidence. UNKNOWN must not be inferred from fulfillment.
