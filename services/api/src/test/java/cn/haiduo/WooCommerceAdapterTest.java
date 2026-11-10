package cn.haiduo;
import cn.haiduo.platform.WooCommerceConnector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
class WooCommerceAdapterTest {
 @Test void refundNetUsesProductBreakdownAndRejectsUnallocatedRefund()throws Exception{
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);var allocated=new java.util.concurrent.atomic.AtomicBoolean(true);
  server.createContext("/wp-json/wc/v3/orders",exchange->{String body=exchange.getRequestURI().getPath().endsWith("/refunds")?(allocated.get()?"[{\"line_items\":[{\"total\":\"-20.00\"}]}]":"[{\"amount\":\"20.00\",\"line_items\":[]}]"):"""
   [{"id":17,"date_modified_gmt":"2026-11-09T10:00:00","status":"processing","currency":"USD","refunds":[{"id":25,"total":"-20.00"}],"line_items":[{"product_id":2,"sku":"BAG","quantity":1,"total":"100.00"}]}]
   """;byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();try{var adapter=new WooCommerceConnector("http://127.0.0.1:"+server.getAddress().getPort(),"ck_test","secret");var order=adapter.orders().getFirst();assertEquals(new BigDecimal("80.00"),order.net());assertEquals("PARTIAL",order.refund());allocated.set(false);assertThrows(IllegalStateException.class,adapter::orders);}finally{server.stop(0);}
 }
 @Test void httpRetryPaginationAndCanonicalSigningAddress()throws Exception{
  var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
  var calls=new java.util.concurrent.atomic.AtomicInteger();
  server.createContext("/wp-json/wc/v3/orders",exchange->{try{
   var query=new TreeMap<String,String>();for(String pair:exchange.getRequestURI().getRawQuery().split("&")){var part=pair.split("=",2);query.put(java.net.URLDecoder.decode(part[0],java.nio.charset.StandardCharsets.UTF_8),java.net.URLDecoder.decode(part[1],java.nio.charset.StandardCharsets.UTF_8));}
   String supplied=query.remove("oauth_signature");boolean valid=Objects.equals(supplied,WooCommerceConnector.signature("http://shop.example/wp-json/wc/v3/orders",query,"secret"));
   int count=calls.incrementAndGet();int status=!valid?401:count==1?429:200;
   String body=query.get("page").equals("2")?"[]":"""
    [{"id":17,"date_modified_gmt":"2026-11-09T10:00:00","status":"pending","currency":"USD","refunds":[],"line_items":[{"product_id":2,"sku":"BAG","quantity":1,"total":"100.00"}]}]
    """;
   exchange.getResponseHeaders().set("X-WP-TotalPages","2");byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
  }catch(Exception failure){exchange.sendResponseHeaders(500,-1);}finally{exchange.close();}});
  server.start();try{var adapter=new WooCommerceConnector("http://127.0.0.1:"+server.getAddress().getPort(),"ck_test","secret","http://shop.example");var orders=adapter.orders();assertEquals(1,orders.size());assertEquals(new BigDecimal("100.00"),orders.getFirst().net());assertEquals(3,calls.get());}finally{server.stop(0);}
 }
 @Test void decimalItemsAndIndependentStates()throws Exception{
  var node=new ObjectMapper().readTree("""
   {"id":17,"date_modified_gmt":"2026-11-09T10:00:00","date_paid_gmt":null,"status":"pending","currency":"USD","refunds":[],"line_items":[{"product_id":2,"variation_id":3,"sku":"BAG","quantity":2,"total":"90.10"},{"product_id":4,"sku":"BOX","quantity":1,"total":"9.90"}]}
   """);
  var order=WooCommerceConnector.map(node);assertEquals(new BigDecimal("100.00"),order.net());assertEquals("PENDING",order.payment());assertEquals("NONE",order.refund());assertEquals("UNFULFILLED",order.fulfillment());assertEquals("3",order.items().getFirst().platformVariantId());
  ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("status","completed");order=WooCommerceConnector.map(node);assertEquals("PAID",order.payment());assertEquals("FULFILLED",order.fulfillment());
  ((com.fasterxml.jackson.databind.node.ObjectNode)node.path("line_items").get(0)).put("total",90.10);assertThrows(IllegalArgumentException.class,()->WooCommerceConnector.map(node));
 }
 @Test void signaturesAreDeterministicAndSecretBound()throws Exception{
  var a=new TreeMap<String,String>();a.put("oauth_timestamp","1");a.put("oauth_nonce","nonce");a.put("oauth_consumer_key","ck_test");var b=new TreeMap<String,String>();b.put("oauth_consumer_key","ck_test");b.put("oauth_nonce","nonce");b.put("oauth_timestamp","1");assertEquals(WooCommerceConnector.signature("http://localhost/orders",a,"secret"),WooCommerceConnector.signature("http://localhost/orders",b,"secret"));assertNotEquals(WooCommerceConnector.signature("http://localhost/orders",a,"secret"),WooCommerceConnector.signature("http://localhost/orders",a,"other"));
 }
}
