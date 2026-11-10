ALTER TABLE store DROP CONSTRAINT store_platform_check;
ALTER TABLE store ADD CONSTRAINT store_platform_check CHECK(platform IN ('MOCK','SHOPIFY','WOOCOMMERCE'));
CREATE TABLE woo_connection(
 tenant_id uuid NOT NULL,
 store_id uuid NOT NULL,
 base_url text NOT NULL UNIQUE,
 key_cipher text NOT NULL,
 secret_cipher text NOT NULL,
 PRIMARY KEY(tenant_id,store_id),
 FOREIGN KEY(tenant_id,store_id) REFERENCES store(tenant_id,id)
);
