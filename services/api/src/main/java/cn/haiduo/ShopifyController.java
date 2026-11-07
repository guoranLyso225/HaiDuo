package cn.haiduo;
import cn.haiduo.platform.*;
import java.util.*;
import java.security.*;
import java.util.HexFormat;
import jakarta.servlet.http.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
@RestController public class ShopifyController {
 private final ShopifyAuthorization auth;private final OperationsController scope;private final JdbcTemplate db;private final Environment env;
 public ShopifyController(ShopifyAuthorization auth,OperationsController scope,JdbcTemplate db,Environment env){this.auth=auth;this.scope=scope;this.db=db;this.env=env;}
 record Start(String shop){}
 @PostMapping("/api/shopify/{id}/authorize") Object start(@PathVariable UUID id,@RequestBody Start r,Principal p,HttpSession s){return auth.start(scope.tenant(p,s,"ADMIN"),id,r.shop(),s,p.getName());}
 @GetMapping("/api/shopify/callback") Object callback(HttpServletRequest request,HttpSession session,Principal p)throws Exception{auth.callback(request,session,p.getName());return Map.of("message","Shopify授权完成，请返回海舵店铺页面");}
 @PostMapping("/api/webhooks/shopify") @ResponseStatus(HttpStatus.ACCEPTED) @Transactional void webhook(HttpServletRequest request)throws Exception{String secret=env.getProperty("SHOPIFY_CLIENT_SECRET");if(secret==null||secret.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Webhook未启用");byte[] raw=request.getInputStream().readNBytes(1048577);if(raw.length>1048576)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);String signature=request.getHeader("X-Shopify-Hmac-Sha256");if(signature==null||!PlatformCrypto.webhook(secret,raw,signature))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);String shop;try{shop=ShopifyConnector.validShop(request.getHeader("X-Shopify-Shop-Domain"));}catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST);}var bindings=db.queryForList("select c.tenant_id,c.store_id from platform_connection c join store s on s.tenant_id=c.tenant_id and s.id=c.store_id where c.shop_domain=? and s.connection_status='CONNECTED'",shop);if(bindings.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);String delivery=request.getHeader("X-Shopify-Webhook-Id"),event=Optional.ofNullable(request.getHeader("X-Shopify-Event-Id")).orElse(delivery),topic=request.getHeader("X-Shopify-Topic");if(delivery==null||event==null||delivery.length()>128||event.length()>128||topic==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);if(!Set.of("orders/create","orders/updated","orders/cancelled","orders/paid","refunds/create").contains(topic))return;var payload=new ObjectMapper().readTree(raw);String gid=topic.equals("refunds/create")?"gid://shopify/Order/"+payload.path("order_id").asText():payload.path("admin_graphql_api_id").asText();if(!gid.matches("gid://shopify/Order/[0-9]+"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);UUID tenant=(UUID)bindings.getFirst().get("tenant_id"),store=(UUID)bindings.getFirst().get("store_id"),id=UUID.randomUUID();int added=db.update("insert into webhook_inbox(id,tenant_id,store_id,delivery_id,event_id,topic,order_gid,payload_hash) values(?,?,?,?,?,?,?,?) on conflict do nothing",id,tenant,store,delivery,event,topic,gid,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));if(added==1)db.update("insert into outbox_event(id,tenant_id,kind,target_id) values(?,?,'SHOPIFY_WEBHOOK',?)",UUID.randomUUID(),tenant,id.toString());}
}
