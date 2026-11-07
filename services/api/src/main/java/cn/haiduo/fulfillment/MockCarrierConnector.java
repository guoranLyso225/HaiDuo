package cn.haiduo.fulfillment;
public final class MockCarrierConnector implements CarrierConnector {
 public String source(){return "MOCK";}
 public Event normalize(Event incoming){String status=switch(incoming.status()){case "IN_TRANSIT","MOVING"->"IN_TRANSIT";case "DELIVERED","SIGNED"->"DELIVERED";case "EXCEPTION","FAILED"->"EXCEPTION";default->throw new IllegalArgumentException("Unknown mock carrier status");};return new Event(incoming.key(),incoming.version(),status,incoming.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS));}
}
