CREATE TABLE exchange_rate_snapshot(id uuid PRIMARY KEY,tenant_id uuid NOT NULL REFERENCES tenant,base_currency text NOT NULL,quote_currency text NOT NULL DEFAULT 'CNY',rate numeric(19,8) NOT NULL CHECK(rate>0),source text NOT NULL,effective_at timestamptz NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(tenant_id,id));
ALTER TABLE sales_order ADD COLUMN exchange_rate_snapshot_id uuid;
ALTER TABLE order_fee ADD COLUMN exchange_rate_snapshot_id uuid;
CREATE FUNCTION capture_order_exchange_rate() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND OLD.exchange_rate_snapshot_id IS NOT NULL AND (NEW.currency,NEW.rate,NEW.rate_source,NEW.rate_at,NEW.tenant_id) IS NOT DISTINCT FROM (OLD.currency,OLD.rate,OLD.rate_source,OLD.rate_at,OLD.tenant_id) THEN NEW.exchange_rate_snapshot_id:=OLD.exchange_rate_snapshot_id;RETURN NEW;END IF;
 NEW.exchange_rate_snapshot_id:=gen_random_uuid();
 INSERT INTO exchange_rate_snapshot(id,tenant_id,base_currency,rate,source,effective_at) VALUES(NEW.exchange_rate_snapshot_id,NEW.tenant_id,NEW.currency,NEW.rate,NEW.rate_source,NEW.rate_at);
 RETURN NEW;
END $$;
CREATE FUNCTION capture_fee_exchange_rate() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND OLD.exchange_rate_snapshot_id IS NOT NULL AND (NEW.currency,NEW.exchange_rate,NEW.rate_source,NEW.effective_at,NEW.tenant_id) IS NOT DISTINCT FROM (OLD.currency,OLD.exchange_rate,OLD.rate_source,OLD.effective_at,OLD.tenant_id) THEN NEW.exchange_rate_snapshot_id:=OLD.exchange_rate_snapshot_id;RETURN NEW;END IF;
 NEW.exchange_rate_snapshot_id:=gen_random_uuid();
 INSERT INTO exchange_rate_snapshot(id,tenant_id,base_currency,rate,source,effective_at) VALUES(NEW.exchange_rate_snapshot_id,NEW.tenant_id,NEW.currency,NEW.exchange_rate,NEW.rate_source,NEW.effective_at);
 RETURN NEW;
END $$;
CREATE TRIGGER order_exchange_rate BEFORE INSERT OR UPDATE ON sales_order FOR EACH ROW EXECUTE FUNCTION capture_order_exchange_rate();
CREATE TRIGGER fee_exchange_rate BEFORE INSERT OR UPDATE ON order_fee FOR EACH ROW EXECUTE FUNCTION capture_fee_exchange_rate();
UPDATE sales_order SET rate=rate;
UPDATE order_fee SET exchange_rate=exchange_rate;
ALTER TABLE sales_order ALTER COLUMN exchange_rate_snapshot_id SET NOT NULL;
ALTER TABLE order_fee ALTER COLUMN exchange_rate_snapshot_id SET NOT NULL;
ALTER TABLE sales_order ADD FOREIGN KEY(tenant_id,exchange_rate_snapshot_id) REFERENCES exchange_rate_snapshot(tenant_id,id);
ALTER TABLE order_fee ADD FOREIGN KEY(tenant_id,exchange_rate_snapshot_id) REFERENCES exchange_rate_snapshot(tenant_id,id);
CREATE FUNCTION immutable_exchange_rate() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Exchange rate snapshots are immutable';END $$;
CREATE TRIGGER immutable_exchange_rate BEFORE UPDATE OR DELETE ON exchange_rate_snapshot FOR EACH ROW EXECUTE FUNCTION immutable_exchange_rate();
