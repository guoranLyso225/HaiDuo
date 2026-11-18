ALTER TABLE sku_approval ADD COLUMN request_key uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE sku_approval ADD CONSTRAINT approval_request_key UNIQUE(tenant_id,applicant,request_key);
ALTER TABLE sku_approval ADD COLUMN reviewed_at timestamptz;
