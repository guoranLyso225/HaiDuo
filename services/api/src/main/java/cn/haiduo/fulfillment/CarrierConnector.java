package cn.haiduo.fulfillment;
import java.time.Instant;
import java.util.UUID;
public interface CarrierConnector {
 record Event(UUID key,long version,String status,Instant occurredAt){}
 String source();
 Event normalize(Event incoming);
}
