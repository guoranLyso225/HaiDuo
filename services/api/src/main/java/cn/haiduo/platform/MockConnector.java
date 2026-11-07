package cn.haiduo.platform;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
public class MockConnector implements PlatformConnector {
 private final Order sample=new Order("MOCK-SYNC-001",Instant.parse("2026-01-01T00:00:00Z"),"USD",new BigDecimal("100.00"),"PAID","NONE",false,"UNFULFILLED",List.of(new Item("MOCK-BAG","DEMO-BAG",1)));
 public List<Order> orders(){return List.of(sample);}public Optional<Order> order(String id){return sample.externalId().equals(id)?Optional.of(sample):Optional.empty();}
}
