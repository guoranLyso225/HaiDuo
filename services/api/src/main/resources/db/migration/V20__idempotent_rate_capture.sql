CREATE OR REPLACE FUNCTION capture_order_exchange_rate() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE previous uuid;
BEGIN
 IF TG_OP='INSERT' THEN
  PERFORM pg_advisory_xact_lock(hashtextextended(NEW.tenant_id::text||':'||length(NEW.store)::text||':'||NEW.store||':'||NEW.external_id,0));
  SELECT exchange_rate_snapshot_id INTO previous FROM sales_order WHERE tenant_id=NEW.tenant_id AND store=NEW.store AND external_id=NEW.external_id;
  IF previous IS NOT NULL THEN NEW.exchange_rate_snapshot_id:=previous;RETURN NEW;END IF;
 ELSIF OLD.exchange_rate_snapshot_id IS NOT NULL AND (NEW.currency,NEW.rate,NEW.rate_source,NEW.rate_at,NEW.tenant_id) IS NOT DISTINCT FROM (OLD.currency,OLD.rate,OLD.rate_source,OLD.rate_at,OLD.tenant_id) THEN NEW.exchange_rate_snapshot_id:=OLD.exchange_rate_snapshot_id;RETURN NEW;
 END IF;
 NEW.exchange_rate_snapshot_id:=gen_random_uuid();
 INSERT INTO exchange_rate_snapshot(id,tenant_id,base_currency,rate,source,effective_at) VALUES(NEW.exchange_rate_snapshot_id,NEW.tenant_id,NEW.currency,NEW.rate,NEW.rate_source,NEW.rate_at);
 RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION capture_fee_exchange_rate() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE previous uuid;
BEGIN
 IF TG_OP='INSERT' THEN
  PERFORM pg_advisory_xact_lock(hashtextextended(NEW.tenant_id::text||':'||NEW.order_id::text||':'||NEW.kind,1));
  SELECT exchange_rate_snapshot_id INTO previous FROM order_fee WHERE tenant_id=NEW.tenant_id AND order_id=NEW.order_id AND kind=NEW.kind;
  IF previous IS NOT NULL THEN NEW.exchange_rate_snapshot_id:=previous;RETURN NEW;END IF;
 ELSIF OLD.exchange_rate_snapshot_id IS NOT NULL AND (NEW.currency,NEW.exchange_rate,NEW.rate_source,NEW.effective_at,NEW.tenant_id) IS NOT DISTINCT FROM (OLD.currency,OLD.exchange_rate,OLD.rate_source,OLD.effective_at,OLD.tenant_id) THEN NEW.exchange_rate_snapshot_id:=OLD.exchange_rate_snapshot_id;RETURN NEW;
 END IF;
 NEW.exchange_rate_snapshot_id:=gen_random_uuid();
 INSERT INTO exchange_rate_snapshot(id,tenant_id,base_currency,rate,source,effective_at) VALUES(NEW.exchange_rate_snapshot_id,NEW.tenant_id,NEW.currency,NEW.exchange_rate,NEW.rate_source,NEW.effective_at);
 RETURN NEW;
END $$;
