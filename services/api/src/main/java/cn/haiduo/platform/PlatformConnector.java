package cn.haiduo.platform;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public interface PlatformConnector {
 record Item(String platformVariantId,String sku,int quantity){}
 record Order(String externalId,Instant updatedAt,String currency,BigDecimal net,String payment,String refund,boolean cancelled,String fulfillment,List<Item> items){}
 List<Order> orders()throws Exception;
 Optional<Order> order(String id)throws Exception;
}
