package cn.haiduo;
import cn.haiduo.platform.PlatformCrypto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.security.MessageDigest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController public class WooWebhookController {
 private final JdbcTemplate db;private final Environment env;
 public WooWebhookController(JdbcTemplate db,Environment env){this.db=db;this.env=env;}
 @PostMapping("/api/webhooks/woocommerce/{store}") @ResponseStatus(HttpStatus.ACCEPTED) @Transactional
 void receive(@PathVariable UUID store,HttpServletRequest request)throws Exception{
  var connections=db.queryForList("select c.tenant_id,c.webhook_cipher from woo_connection c join store s on s.tenant_id=c.tenant_id and s.id=c.store_id where c.store_id=? and s.connection_status='CONNECTED'",store);
  if(connections.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  var connection=connections.getFirst();if(connection.get("webhook_cipher")==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"尚未配置Webhook签名密钥");
  byte[] raw=request.getInputStream().readNBytes(1048577);if(raw.length>1048576)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
  String signature=request.getHeader("X-WC-Webhook-Signature");
  String secret=PlatformCrypto.decrypt(env.getRequiredProperty("TOKEN_ENCRYPTION_KEY"),(String)connection.get("webhook_cipher"));
  if(signature==null||!PlatformCrypto.webhook(secret,raw,signature))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  String topic=request.getHeader("X-WC-Webhook-Topic");if(!Set.of("order.created","order.updated").contains(topic==null?"":topic))return;
  String delivery=request.getHeader("X-WC-Webhook-Delivery-ID");if(delivery==null||delivery.isBlank()||delivery.length()>128)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  String order;try{order=new ObjectMapper().readTree(raw).path("id").asText();}catch(Exception bad){throw new ResponseStatusException(HttpStatus.BAD_REQUEST);}
  if(!order.matches("[1-9][0-9]*"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));UUID id=UUID.randomUUID(),tenant=(UUID)connection.get("tenant_id");
  int added=db.update("insert into webhook_inbox(id,tenant_id,store_id,delivery_id,event_id,topic,order_gid,payload_hash) values(?,?,?,?,?,?,?,?) on conflict do nothing",id,tenant,store,delivery,"woo:"+hash,topic,order,hash);
  if(added==1)db.update("insert into outbox_event(id,tenant_id,kind,target_id) values(?,?,'WOO_WEBHOOK',?)",UUID.randomUUID(),tenant,id.toString());
 }
}
