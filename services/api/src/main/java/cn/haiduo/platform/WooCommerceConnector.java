package cn.haiduo.platform;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Read-only Woo REST v3 adapter. No credential-bearing URI is logged. */
public class WooCommerceConnector implements PlatformConnector {
 private final String base,key,secret,signingBase;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final ObjectMapper json=new ObjectMapper();
 public WooCommerceConnector(String base,String key,String secret){this(base,key,secret,base);}
 public WooCommerceConnector(String base,String key,String secret,String signingBase){this.base=base;this.key=key;this.secret=secret;this.signingBase=signingBase;}
 static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20").replace("*","%2A").replace("%7E","~");}
 public static String signature(String url,SortedMap<String,String> query,String secret)throws Exception{
  String params=query.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec((encode(secret)+"&").getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
  return Base64.getEncoder().encodeToString(mac.doFinal(("GET&"+encode(url)+"&"+encode(params)).getBytes(StandardCharsets.UTF_8)));
 }
 private HttpResponse<String> get(String path,Map<String,String> parameters)throws Exception{
  String url=base+"/wp-json/wc/v3/"+path;
  for(int attempt=0;attempt<3;attempt++){
   var query=new TreeMap<>(parameters);
   query.put("oauth_consumer_key",key);query.put("oauth_nonce",UUID.randomUUID().toString());query.put("oauth_timestamp",String.valueOf(Instant.now().getEpochSecond()));query.put("oauth_signature_method","HMAC-SHA256");
   query.put("oauth_signature",signature(signingBase+"/wp-json/wc/v3/"+path,query,secret));
   String q=query.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
   HttpResponse<String> response;
   try{response=client.send(HttpRequest.newBuilder(URI.create(url+"?"+q)).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());}
   catch(java.io.IOException unavailable){throw new IllegalStateException("WooCommerce network unavailable");}
   catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("WooCommerce request interrupted");}
   if(response.statusCode()==200)return response;
   if(response.statusCode()==404&&path.startsWith("orders/"))return response;
   if((response.statusCode()==429||response.statusCode()>=500)&&attempt<2){Thread.sleep(250L*(1L<<attempt));continue;}
   throw new IllegalStateException("WooCommerce HTTP "+response.statusCode());
  }
  throw new IllegalStateException("WooCommerce retry limit");
 }
 public List<Order> orders()throws Exception{
  var result=new ArrayList<Order>();long deadline=System.nanoTime()+Duration.ofSeconds(25).toNanos();
  for(int page=1;page<=20;page++){
   if(System.nanoTime()>deadline)throw new IllegalStateException("WooCommerce sync deadline");
   var response=get("orders",Map.of("per_page","50","page",String.valueOf(page),"orderby","id","order","asc"));
   var nodes=json.readTree(response.body());if(!nodes.isArray())throw new IllegalStateException("Invalid Woo orders");
   for(var node:nodes)result.add(decode(node));
   int pages=Integer.parseInt(response.headers().firstValue("X-WP-TotalPages").orElse("1"));
   if(page>=pages)return result;
  }
  throw new IllegalStateException("WooCommerce maximum 1000 orders per read; use a narrower integration scope");
 }
 public Optional<Order> order(String id)throws Exception{if(!id.matches("[1-9][0-9]*"))throw new IllegalArgumentException("Invalid Woo order id");var response=get("orders/"+id,Map.of());return response.statusCode()==404?Optional.empty():Optional.of(decode(json.readTree(response.body())));}
 private Order decode(JsonNode node)throws Exception{
  Order original=map(node);if(node.path("refunds").isEmpty())return original;
  var response=get("orders/"+original.externalId()+"/refunds",Map.of("per_page","100"));
  if(Integer.parseInt(response.headers().firstValue("X-WP-TotalPages").orElse("1"))>1)throw new IllegalStateException("Woo refund page limit; manual review required");
  var refunds=json.readTree(response.body());if(!refunds.isArray())throw new IllegalStateException("Missing refund breakdown");
  BigDecimal refunded=BigDecimal.ZERO;
  for(var refund:refunds){if(refund.path("line_items").isEmpty())throw new IllegalStateException("Unallocated Woo refund requires manual reconciliation");for(var line:refund.path("line_items")){if(!line.path("total").isTextual())throw new IllegalStateException("Invalid refund amount");refunded=refunded.add(new BigDecimal(line.path("total").asText()).abs());}}
  BigDecimal net=original.net().subtract(refunded);if(net.signum()<0)throw new IllegalStateException("Refund exceeds item income");
  return new Order(original.externalId(),original.updatedAt(),original.currency(),net,original.payment(),original.refund(),original.cancelled(),original.fulfillment(),original.items());
 }
 public static Order map(JsonNode node){
  var items=new ArrayList<Item>();BigDecimal net=BigDecimal.ZERO;
  for(var line:node.path("line_items")){
   if(!line.path("quantity").canConvertToInt()||!line.path("quantity").isIntegralNumber())throw new IllegalArgumentException("Invalid Woo quantity");
   int quantity=line.path("quantity").asInt();if(quantity<1||quantity>100000)throw new IllegalArgumentException("Invalid Woo quantity");
   if(!line.path("total").isTextual())throw new IllegalArgumentException("Woo amounts must be decimal strings");
   net=net.add(new BigDecimal(line.path("total").asText()));
   String variant=line.path("variation_id").asLong()>0?line.path("variation_id").asText():line.path("product_id").asText();
   items.add(new Item(variant,line.path("sku").asText(),quantity));
  }
  if(items.isEmpty())throw new IllegalArgumentException("Woo order has no items");
  String status=node.path("status").asText(),currency=node.path("currency").asText();
  if(!currency.matches("[A-Z]{3}")||net.signum()<0)throw new IllegalArgumentException("Invalid Woo money");
  String date=node.path("date_modified_gmt").asText();Instant updated=LocalDateTime.parse(date).toInstant(ZoneOffset.UTC);
  boolean paid=!node.path("date_paid_gmt").isNull()&&!node.path("date_paid_gmt").asText().isBlank()||Set.of("processing","completed","refunded").contains(status);
  return new Order(node.path("id").asText(),updated,currency,net,paid?"PAID":status.equals("failed")?"FAILED":"PENDING",status.equals("refunded")?"REFUNDED":node.path("refunds").size()>0?"PARTIAL":"NONE",Set.of("cancelled","failed").contains(status),status.equals("completed")?"FULFILLED":"UNFULFILLED",items);
 }
}
